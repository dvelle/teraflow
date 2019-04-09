package com.terazyte.flow.docker

import java.io.File
import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import akka.http.scaladsl.{ClientTransport, Http}
import com.terazyte.flow.config.ResourceConfig
import com.terazyte.flow.job.TaskExecResult
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import spray.json._
import akka.stream.Materializer
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import akka.stream.scaladsl.Flow
import akka.util.ByteString
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

case class DockerContainer(alias: String, container: String, host: Option[String]) extends ResourceConfig with SprayJsonSupport {

  def execCmd(value: String)(implicit mat: Materializer, system: ActorSystem): Either[Throwable, String] = {
    import system.dispatcher
    val settings =
      ConnectionPoolSettings(system).withTransport(new DockerSockTransport)

    implicit val execIdFormat = jsonFormat1(ExecId)
    implicit val containerExecFormat = jsonFormat7(ContainerExecRequest)
    val cmd = Seq("bash","-c",value)
//    val registerExecCmd = ContainerExecRequest(value.split(" ")).toJson.compactPrint
val registerExecCmd = ContainerExecRequest(cmd).toJson.compactPrint

    val httpURI =
      s"""http://localhost/v1.24/containers/${container}/exec"""
    val req = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$httpURI",
      entity = HttpEntity(ContentTypes.`application/json`, registerExecCmd))


   val opsResponse = for {
      response <- Http().singleRequest(req, settings = settings)
      execId <- Unmarshal(response.entity).to[ExecId]
      strResponse <- execCmdWithId(execId,settings)
    } yield strResponse


    Try(Await.result(opsResponse, 1.hour)).toEither

  }

  private def execCmdWithId(execId: ExecId,settings: ConnectionPoolSettings)(implicit mat: Materializer, system: ActorSystem): Future[String] = {
    import system.dispatcher
    val startCmd = """{
                     |  "Detach": false,
                     |  "Tty": true
                     |}""".stripMargin

    val httpURI =
      s"""http://localhost/v1.24/exec/${execId.Id}/start"""
    val req = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$httpURI",
      entity = HttpEntity(ContentTypes.`application/json`, startCmd))

    val response = Http()
      .singleRequest(req, settings = settings)
      .map(x => x.entity)

    response.flatMap(entity => Unmarshaller.stringUnmarshaller(entity))

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