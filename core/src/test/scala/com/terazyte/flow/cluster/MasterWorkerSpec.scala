package com.terazyte.flow.cluster
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import com.terazyte.flow.cluster.Master.SubmitJob
import com.terazyte.flow.services.InMemoryJobState
import com.terazyte.flow.task.CmdDef
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.pattern.ask
import akka.util.Timeout
import com.terazyte.flow.config.IdGenerator
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.collection.immutable.Queue
import scala.concurrent.Await

class MasterWorkerSpec
    extends TestKit(ActorSystem("SparkSubmitSpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  "submission of job should return the job id" in {
    implicit val timeout = Timeout(1.seconds)
    val idGenerator = new IdGenerator {
      override def generate(prefix: String): String = s"100"
    }
    val master   = system.actorOf(Master.props(new InMemoryJobState))
    val execStep = CmdDef("ls -lrt .", System.getProperty("user.home"))
    master ! SubmitJob("local-job",Seq(), Queue(execStep), idGenerator)
    expectMsg("100")
    Thread.sleep(1000)

  }

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

}
