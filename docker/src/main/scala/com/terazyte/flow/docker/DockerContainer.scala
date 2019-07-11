package com.terazyte.flow.docker

import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.{Charset, StandardCharsets}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import akka.http.scaladsl.{ClientTransport, Http}
import com.terazyte.flow.config.{DataTransferable, ResourceConfig}

import sys.process._
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import spray.json._
import akka.stream.{KillSwitches, Materializer}
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import akka.stream.scaladsl.{Flow, Keep, RestartSource, Sink, Source}
import akka.util.{ByteString, CompactByteString}
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

case class ContainerExecRequest(Cmd: Seq[String],
                                AttachStdin: Boolean = false,
                                AttachStdout: Boolean = true,
                                AttachStderr: Boolean = true,
                                DetachKeys: String = "",
                                Tty: Boolean = true,
                                Env: Seq[String] = Seq.empty)

case class ExecId(Id: String)

case class DockerContainer(alias: String, container: String, host: Option[String])
    extends ResourceConfig
    with SprayJsonSupport with DataTransferable {

  override def execCopy(from: String, to: String): Unit = {
    val exitCode = Process(Seq("docker","cp",from, s"${container}:${to}")).!
    if(exitCode != 0) throw new IllegalArgumentException(s"Unable to copy the file : ${from}")
  }

  def execCmd(value: String)(logFn : String => Unit)(implicit mat: Materializer, system: ActorSystem): Either[Throwable, String] = {
    val idleDuration: FiniteDuration = 10.seconds
    import system.dispatcher
    val isWin = System.getProperty("os.name").toLowerCase().contains("win")
    val settings = if (!isWin) {
      ConnectionPoolSettings(system).withTransport(new DockerSockTransport)
    } else {
      ConnectionPoolSettings(system)
    }

    implicit val execIdFormat        = jsonFormat1(ExecId)
    implicit val containerExecFormat = jsonFormat7(ContainerExecRequest)
    val cmd                          = Seq("bash", "-c", value)
//    val registerExecCmd = ContainerExecRequest(value.split(" ")).toJson.compactPrint
    val registerExecCmd = ContainerExecRequest(cmd).toJson.compactPrint

    val httpURI =
      s"""http://localhost/v1.24/containers/${container}/exec"""
    val req = HttpRequest(method = HttpMethods.POST,
                          uri = s"$httpURI",
                          entity = HttpEntity(ContentTypes.`application/json`, registerExecCmd))

    val opsResponse = for {
      response    <- Http().singleRequest(req, settings = settings)
      execId      <- Unmarshal(response.entity).to[ExecId]
     logStream <- execCmdWithId(execId, isWin, settings).idleTimeout(idleDuration)
       .viaMat(KillSwitches.single)(Keep.right)
       .map(x => x.utf8String)
       .toMat(Sink.foreach[String] { logLine =>
         logFn(logLine)

       })(Keep.right)
       .run()
    } yield logStream

    Try(Await.result(opsResponse, 1.hour).toString).toEither

  }

  private def execCmdWithId(execId: ExecId, isWin: Boolean, settings: ConnectionPoolSettings)(
      implicit mat: Materializer,
      system: ActorSystem): Source[ByteString, Future[Any]] = {
    import system.dispatcher
    val startCmd = """{
                     |  "Detach": false,
                     |  "Tty": false
                     |}""".stripMargin

    val httpURI = if(!isWin){
      s"""http://localhost/v1.30/exec/${execId.Id}/start"""
    } else {
      s"""http://localhost:2375/v1.30/exec/${execId.Id}/start"""
    }

    val req = HttpRequest(method = HttpMethods.POST,
                          uri = s"$httpURI",
                          entity = HttpEntity(ContentTypes.`application/json`, startCmd))

    val logStream = Source
      .fromFutureSource {
        val response = Http().singleRequest(req, settings = settings)
        response.map(resp => {
          resp.status match {
            case OK =>
              resp.entity.withoutSizeLimit().dataBytes

            case code =>
              val text  = resp.entity.dataBytes.map(_.utf8String)
              val error = s"Unexpected status code: $code, $text"

              Source.failed(new RuntimeException(error))
          }
        })
      }
      .recover {
        case e: RuntimeException => CompactByteString(e.getMessage)
      }
//    RestartSource.withBackoff(
//      minBackoff = 5.seconds,
//      maxBackoff = 5.seconds,
//      randomFactor = 0.2
//    ) { () =>
//      Source
//        .fromFutureSource {
//          val response = Http().singleRequest(req, settings = settings)
//          response.map(resp => {
//            resp.status match {
//              case OK =>
//                resp.entity.withoutSizeLimit().dataBytes
//
//              case code =>
//                val text  = resp.entity.dataBytes.map(_.utf8String)
//                val error = s"Unexpected status code: $code, $text"
//
//                Source.failed(new RuntimeException(error))
//            }
//          })
//        }
//        .recover {
//          case e: RuntimeException => CompactByteString(e.getMessage)
//        }
    logStream

//    val response = Http()
//      .singleRequest(req, settings = settings)
//      .map(x => x.entity)
//
//    response.flatMap(entity => Unmarshaller.stringUnmarshaller(entity))

  }

}

class DockerSockTransport extends ClientTransport {

  override def connectTo(host: String, port: Int, settings: ClientConnectionSettings)(
      implicit system: ActorSystem
  ): Flow[ByteString, ByteString, Future[Http.OutgoingConnection]] = {
    // ignore everything for now

    UnixDomainSocket()
      .outgoingConnection(new File("/var/run/docker.sock"))
      .mapMaterializedValue { x ⇒
        Future.successful(
          Http.OutgoingConnection(
            InetSocketAddress.createUnresolved(host, port),
            InetSocketAddress.createUnresolved(host, port)
          )
        )
      }
  }
}
