import sbt.Keys._

object CommonSettings {

  lazy val versions = new {

    val commonsCodec      = "1.9"
    val commonsFileupload = "1.3.1"
    val commonsIo         = "2.4"
    val commonsLang       = "2.6"
    val guava             = "19.0"
    val guice             = "4.0"
    val jackson           = "2.8.4"
    val jodaConvert       = "1.2"
    val jodaTime          = "2.5"
    val junit             = "4.12"
    val libThrift         = "0.10.0"
    val logback           = "1.1.7"
    val mockito           = "1.9.5"
    val mustache          = "0.8.18"
    val nscalaTime        = "2.14.0"
    val scalaCheck        = "1.13.4"
    val scalaGuice        = "4.1.0"
    val scalaTest         = "3.0.0"
    val servletApi        = "2.5"
    val slf4j             = "1.7.21"
    val snakeyaml         = "1.12"
    val specs2            = "2.4.17"
    val akka              = "2.5.12"
    val akkaHttp          = "10.1.5"
  }

  lazy val scalaCompilerOptions = scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Xlint",
    "-Ywarn-unused-import"
  )

}
