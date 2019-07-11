package com.terazyte.flow.cli

import com.terazyte.flow.parser.ConfigParser
import com.terazyte.flow.steps.RemoteCmdStep
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import com.terazyte.flow.config.ResourceConfig
import com.terazyte.flow.job.JobController
import com.terazyte.flow.job.JobController.StartJob
import com.terazyte.flow.protocol.SecretConfigProtocol
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, WordSpecLike}
import net.jcazevedo.moultingyaml._

import sys.process._
import scala.io.Source

class SparkSubmitSpec
    extends TestKit(ActorSystem("SparkSubmitSpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with SecretConfigProtocol {

  val resourceConfig = Source
    .fromFile(getClass.getResource("/resources.yml").getFile)
    .getLines()
    .mkString("\n")
    .parseYaml
    .convertTo[Seq[ResourceConfig]]
  val sparkConfigPath = getClass.getResource("/spark.submit.yml").getFile
  val config          = ConfigParser.parse(sparkConfigPath)
  val sparkCompose    = getClass.getResource("/spark/docker-compose.yml").getFile

  /**
    * Start the spark docker container for spark
    */
  override def beforeAll: Unit = {

//    val exit = Seq("docker-compose", "-f", sparkCompose, "up", "-d").!
//    if (exit != 0) {
//      throw new IllegalArgumentException("Unable to run docker-compose")
//    } else {
//      Thread.sleep(10000)
//    }
  }

  override def afterAll: Unit = {
    //TestKit.shutdownActorSystem(system)
    //Shut down the docker container
//    val exit = Seq("docker-compose", "-f", sparkCompose, "down").!
//    if (exit != 0) {
//      throw new IllegalArgumentException("Unable to run docker-compose")
//    }
  }

  "parse the spark flow def" in {

    config.tasks.length shouldBe (4)
    config.tasks.filter(x => x.isInstanceOf[RemoteCmdStep]).length shouldBe (1)
  }

  "run spark submit" in {
    val controller = system.actorOf(JobController.props(config, resourceConfig), "teraflow-controller")
    controller ! StartJob()
  }

}
