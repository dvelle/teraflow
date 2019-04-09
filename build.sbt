import CommonSettings._
import sbtrelease.ReleaseStateTransformations._

lazy val teraflow = (project in file("."))
  .settings(buildSettings)
  .settings(baseSettings)
  .settings(
    organization := "com.terazyte",
    moduleName := "teraflow-tool"
  )
  .aggregate(rest, core, tool)

lazy val core = (project in file("core"))
  .settings(projectSettings)
  .settings(
    name := "teraflow-core",
    moduleName := "teraflow-core",
    libraryDependencies ++= Seq(
      "javax.inject"           % "javax.inject"              % "1",
      "ch.qos.logback"         % "logback-classic"           % "1.2.3",
      "org.fusesource.jansi"   % "jansi"                     % "1.17.1",
      "com.typesafe.akka"      %% "akka-actor"               % versions.akka,
      "org.eclipse.jgit"       % "org.eclipse.jgit"          % "5.2.0.201812061821-r",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1",
      "net.jcazevedo"          %% "moultingyaml"             % "0.4.0",
      "io.spray"               %% "spray-json"               % "1.3.4",
      "com.hierynomus"         % "sshj"                      % "0.27.0",
    )
  )

lazy val tool = (project in file("tool"))
  .settings(projectSettings)
  .settings(
    name := "teraflow-tool",
    moduleName := "teraflow-tool",
    packMain := Map("teraflow" -> "com.terazyte.flow.cli.Teraflow"),
    libraryDependencies ++= Seq(
      "ch.qos.logback"         % "logback-classic"           % "1.2.3",
      "org.fusesource.jansi"   % "jansi"                     % "1.17.1",
      "com.typesafe.akka"      %% "akka-actor"               % versions.akka,
      "org.eclipse.jgit"       % "org.eclipse.jgit"          % "5.2.0.201812061821-r",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1",
      "com.hierynomus"         % "sshj"                      % "0.27.0",
      "com.github.scopt"       %% "scopt"                    % "3.7.1"
    )
  )
  .dependsOn(core, aws, docker)
  .enablePlugins(PackPlugin)

lazy val aws = (project in file("aws"))
  .settings(projectSettings)
  .settings(
    name := "teraflow-aws",
    moduleName := "teraflow-aws",
    libraryDependencies ++= Seq(
      "com.github.seratch" %% "awscala-s3"     % "0.8.+",
      "com.github.seratch" %% "awscala-emr" % "0.8.+"
    )
  )
  .dependsOn(core)

lazy val docker = (project in file("docker"))
  .settings(projectSettings)
  .settings(
    name := "teraflow-docker",
    moduleName := "teraflow-docker",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % versions.akka,
      "com.typesafe.akka" %% "akka-http" % versions.akkaHttp,
      "io.spray" %%  "spray-json" % "1.3.4",
      "com.lightbend.akka" %% "akka-stream-alpakka-unix-domain-socket" % "0.20",
      "com.typesafe.akka" %% "akka-http-spray-json" % versions.akkaHttp,
    )
  )
  .dependsOn(core)

lazy val rest = (project in file("rest"))
  .settings(projectSettings)
  .settings(
    name := "teraflow-rest",
    moduleName := "teraflow-rest",
    libraryDependencies ++= Seq(
      "javax.inject"             % "javax.inject"          % "1",
      "ch.qos.logback"           % "logback-classic"       % "1.2.3",
      "com.h2database"           % "h2"                    % "1.4.196",
      "com.softwaremill.macwire" %% "macros"               % "2.3.1",
      "com.typesafe.akka"        %% "akka-stream"          % versions.akka,
      "com.typesafe.akka"        %% "akka-http"            % versions.akkaHttp,
      "io.spray"                 %% "spray-json"           % "1.3.4",
      "com.zaxxer"               % "nuprocess"             % "1.2.4",
      "com.typesafe.akka"        %% "akka-http-spray-json" % versions.akkaHttp
    )
  )

lazy val buildSettings = Seq(
  cancelable in Global := true,
  organizationName := "terazyte",
  startYear := Some(2019),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
  mainClass in assembly := Some(appMain),
  scalaVersion := "2.12.6",
  assemblyJarName in assembly := "teraflow.jar",
  assemblyMergeStrategy in assembly := {
    case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.discard
    case "application.conf"                            => MergeStrategy.concat
    case "unwanted.txt"                                => MergeStrategy.discard
    case PathList(ps @ _*) if ps.last endsWith ".properties" =>
      MergeStrategy.first
    case PathList(ps @ _*) if ps.last endsWith "BUILD" =>
      MergeStrategy.discard
    case PathList(ps @ _*) if ps.last endsWith "txt" =>
      MergeStrategy.discard
    case PathList(ps @ _*) if ps.last endsWith ".default" =>
      MergeStrategy.discard
    case PathList(ps @ _*) if ps.last endsWith "class" => MergeStrategy.first
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  scalaModuleInfo := scalaModuleInfo.value.map(_.withOverrideScalaVersion(true)),
  fork in Test := true
)

lazy val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "org.mockito"                % "mockito-core"    % versions.mockito % Test,
    "org.scalacheck"             %% "scalacheck"     % versions.scalaCheck % Test,
    "org.scalatest"              %% "scalatest"      % versions.scalaTest % Test,
    "org.specs2"                 %% "specs2-core"    % versions.specs2 % Test,
    "org.specs2"                 %% "specs2-junit"   % versions.specs2 % Test,
    "org.specs2"                 %% "specs2-mock"    % versions.specs2 % Test,
    "ch.qos.logback"             % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.0"
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  scalaCompilerOptions,
  javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked"),
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

lazy val projectSettings = baseSettings ++ buildSettings ++ Seq(
  organization := "com.terazyte"
)
