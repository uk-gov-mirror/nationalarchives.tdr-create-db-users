package uk.gov.nationalarchives.db.users

import scalikejdbc._
import uk.gov.nationalarchives.db.users.Config._

import java.io.{InputStream, OutputStream}
import java.nio.charset.Charset

class Lambda {

  def process(inputStream: InputStream, outputStream: OutputStream) = {
    val apiUser = createUser(lambdaConfig.consignmentApiUser)
    sql"GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO $apiUser;".execute.apply()
    sql"GRANT USAGE on consignment_sequence_id to $apiUser;".execute.apply()

    //Grants permissions for any new tables that are created.
    //This is not needed for the migrations user as it will be creating the tables so it will own them/
    sql"ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO $apiUser;".execute().apply()

    val migrationsUser = createUser(lambdaConfig.migrationsUser)
    sql"GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO $migrationsUser;".execute.apply()
    sql"GRANT ALL PRIVILEGES ON consignment_sequence_id TO $migrationsUser;".execute.apply()

    outputStream.write("Users created successfully".getBytes(Charset.defaultCharset()))
  }

  def createUser(username: String): SQLSyntax = {
    //createUnsafely is needed as the usual interpolation returns ERROR: syntax error at or near "$1"
    //There is a similar issue here https://github.com/scalikejdbc/scalikejdbc/issues/320
    val user = sqls.createUnsafely(username)
    sql"CREATE USER $user".execute().apply()
    sql"GRANT CONNECT ON DATABASE consignmentapi TO $user;".execute.apply()
    sql"GRANT USAGE ON SCHEMA public TO $user;".execute.apply()
    sql"GRANT rds_iam TO $user;".execute().apply()
    user
  }
}
