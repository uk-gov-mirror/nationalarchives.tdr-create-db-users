package uk.gov.nationalarchives.db.users

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import Config._
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.{Parameters, ResponseDefinitionTransformer}
import com.github.tomakehurst.wiremock.http.{Request, ResponseDefinition}
import scalikejdbc._

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

import io.circe.generic.auto._
import io.circe.parser.decode

class LambdaSpec extends AnyFlatSpec with Matchers {

  val kmsWiremock = new WireMockServer(new WireMockConfiguration().port(9001).extensions(new ResponseDefinitionTransformer {
    override def transform(request: Request, responseDefinition: ResponseDefinition, files: FileSource, parameters: Parameters): ResponseDefinition = {
      case class KMSRequest(CiphertextBlob: String)
      decode[KMSRequest](request.getBodyAsString) match {
        case Left(err) => throw err
        case Right(req) =>
          val charset = Charset.defaultCharset()
          val plainText = charset.newDecoder.decode(ByteBuffer.wrap(req.CiphertextBlob.getBytes(charset))).toString
          ResponseDefinitionBuilder
            .like(responseDefinition)
            .withBody(s"""{"Plaintext": "$plainText"}""")
            .build()
      }
    }
    override def getName: String = ""
  }))

  def prepareKmsMock() = {
    kmsWiremock.stubFor(post(urlEqualTo("/")))
    kmsWiremock.start()
  }

  def prepareDb(username: String) = {
    val user = sqls.createUnsafely(username)
    sql"DROP ROLE IF EXISTS rds_iam".execute().apply()
    sql"CREATE ROLE rds_iam".execute().apply()
    sql"CREATE TABLE IF NOT EXISTS Test();".execute().apply()
    sql"CREATE SEQUENCE IF NOT EXISTS consignment_sequence_id;".execute().apply()
    val userCount = sql"SELECT count(*) as userCount FROM pg_roles WHERE rolname = $username".map(_.int("userCount")).list.apply.head
    if (userCount > 0) {
      sql"ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE SELECT, INSERT, UPDATE ON TABLES FROM $user;".execute().apply()
      sql"REVOKE CONNECT ON DATABASE consignmentapi FROM $user;".execute.apply()
      sql"REVOKE USAGE ON SCHEMA public FROM $user;".execute.apply()
      sql"REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM $user;".execute.apply()
      sql"REVOKE ALL PRIVILEGES ON consignment_sequence_id FROM $user;".execute().apply()
      sql"DROP USER IF EXISTS $user;".execute().apply()
    }
  }

  def checkPrivileges(username: String, expectedPrivileges: List[String]) = {
    val privileges: List[String] = sql"SELECT privilege_type FROM information_schema.table_privileges WHERE grantee=$username"
      .map(rs => rs.string("privilege_type"))
      .list.apply()
    privileges.sorted should equal(expectedPrivileges)
  }

  "The process method" should s"create the users with the correct parameters" in {
    prepareKmsMock()
    prepareDb(lambdaConfig.consignmentApiUser)
    prepareDb(lambdaConfig.migrationsUser)
    new Lambda().process(null, new ByteArrayOutputStream())
    checkPrivileges(lambdaConfig.consignmentApiUser, List("INSERT", "SELECT", "UPDATE"))
    checkPrivileges(lambdaConfig.migrationsUser, List("DELETE", "INSERT", "REFERENCES", "SELECT", "TRIGGER", "TRUNCATE", "UPDATE"))
    kmsWiremock.stop()
  }
}
