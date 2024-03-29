/*
 * Copyright 2019 Terazyte
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terazyte.flow.cli

import java.io.File
import java.nio.file.{Path, Paths}
import java.security.Security
import java.util.UUID

import akka.actor.ActorSystem
import com.terazyte.flow.config.ResourceConfig
import com.terazyte.flow.job.{Completed, JobController, JobState, Stopped}
import com.terazyte.flow.job.JobController.StartJob
import com.terazyte.flow.parser.ConfigParser
import com.terazyte.flow.protocol.ResourceConfigProtocol
import com.terazyte.flow.services.{InMemoryJobState, LocalJobExecutor}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import net.jcazevedo.moultingyaml._

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.Source
import scala.util.{Failure, Success, Try}

object Teraflow extends App {

  System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")

  Security.addProvider(new BouncyCastleProvider)
  val appArgs = if (args.length == 1 && !args(0).startsWith("-")) args.tail else args

  val appConfig = CLIConfig.cliParser.parse(appArgs, CLIConfig()) match {
    case Some(config) =>
      if (config.version) {
        showVersion()
      }
      val system       = ActorSystem("Teraflow")
      val flowExecutor = new Teraflow(system, config)
      val configPath = if (args.length == 1) {
        val fileName = args(0) + config.configPath.substring(config.configPath.lastIndexOf('/') + 1)
        val prevPart = config.configPath.substring(0, config.configPath.lastIndexOf('/') + 1)
        prevPart + fileName
      } else config.configPath

      flowExecutor.launch(configPath.replaceFirst("~", System.getProperty("user.home")))
    case None =>
      println("Failed parsing the params")
  }

  def showVersion(): Unit = {
    val progName = "Teraflow"
    val progVersion = System.getProperty("prog.version") match {
      case x if x != null && x.nonEmpty => x
      case _                            => "1.0.0"
    }
    val progRevision = System.getProperty("prog.revision") match {
      case x if x != null && x.nonEmpty => x
      case _                            => UUID.randomUUID().toString
    }
    println(s"${progName} - ${progVersion}")
    println(s"Revision: ${progRevision}")
    sys.exit(0)
  }

}

class Teraflow(system: ActorSystem, cliConfig: CLIConfig) extends ResourceConfigProtocol {

  def launch(path: String): Unit = {
    val configFile = new File(path)
    path match {
      case f if configFile.exists() =>
        val config = ConfigParser.parse(f)

        val optResources = config.resources.fold[Option[Seq[ResourceConfig]]](Some(Seq.empty[ResourceConfig])) {
          resourcePath =>
            val resourceFile = if (resourcePath.startsWith(".")) {
              val current = configFile.getParent
              Paths.get(current, resourcePath).toAbsolutePath.toFile
            } else
              new File(resourcePath)

            if (resourceFile.isFile) {
              val tryResources = Try(
                Source
                  .fromFile(resourceFile)
                  .getLines()
                  .mkString("\n")
                  .parseYaml
                  .convertTo[Seq[ResourceConfig]])
              tryResources match {
                case Success(value) => Some(value)
                case Failure(exception) =>
                  println(exception.getMessage)
                  None

              }
            } else {
              println(s"File not found: ${resourceFile.toString}")
              None
            }
        }

        optResources match {
          case Some(resources) =>
            val controller  = system.actorOf(JobController.props(config, resources), "teraflow-controller")
            val jobExecutor = new LocalJobExecutor(new InMemoryJobState, system)
            val jobId       = Await.result(jobExecutor.submit(config.project, resources, Queue(config.tasks: _*)), 2.seconds)

            var jobCompleted = false
            while (!jobCompleted) {
              val hasJobCompleted = jobExecutor.getJobStatus(jobId).map {
                case Some(JobState(_, _, currentStatus, _, _)) =>
                  currentStatus match {
                    case Completed => true
                    case Stopped   => true
                    case _         => false
                  }
              }

              jobCompleted = Await.result(hasJobCompleted, 2.seconds)
              if(!jobCompleted){
                Thread.sleep(1000)
              }
            }

            controller ! StartJob()
          case None =>
            system.terminate().onComplete(_ => sys.exit(1))

        }

      case _ =>
        println(s"Config file at ${path} not found")
        system.terminate()

    }

  }

}
