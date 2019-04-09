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
import com.terazyte.flow.job.JobController
import com.terazyte.flow.job.JobController.StartJob
import com.terazyte.flow.parser.ConfigParser
import com.terazyte.flow.protocol.SecretConfigProtocol
import org.bouncycastle.jce.provider.BouncyCastleProvider
import net.jcazevedo.moultingyaml._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.{Failure, Success, Try}

object Launcher extends App {

  System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")

  Security.addProvider(new BouncyCastleProvider)
  val appArgs = if (args.length == 1 && !args(0).startsWith("-")) args.tail else args

  val appConfig = CLIConfig.cliParser.parse(appArgs, CLIConfig()) match {
    case Some(config) =>
      if (config.version) {
        showVersion()
      }
      val system   = ActorSystem("Launcher")
      val launcher = new Launcher(system, config)
      val configPath = if (args.length == 1) {
        val fileName = args(0) + config.configPath.substring(config.configPath.lastIndexOf('/') + 1)
        val prevPart = config.configPath.substring(0, config.configPath.lastIndexOf('/') + 1)
        prevPart + fileName
      } else config.configPath

      launcher.launch(configPath.replaceFirst("~", System.getProperty("user.home")))
    case None =>
      println("Failed parsing the params")
  }

  def showVersion(): Unit = {
    val progName = "Launcher"
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

class Launcher(system: ActorSystem, cliConfig: CLIConfig) extends SecretConfigProtocol {

  def launch(path: String): Unit = {
    val configFile = new File(path)
    path match {
      case f if configFile.exists() =>
        val config = ConfigParser.parse(f)
        val modConfig = if (cliConfig.stages.size > 0) {
          val filteredStages = config.stages.filterKeys(k => cliConfig.stages.contains(k))
          config.copy(stages = filteredStages)
        } else {
          config
        }
        val optResources = modConfig.resources.fold[Option[Seq[ResourceConfig]]](Some(Seq.empty[ResourceConfig])) {
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
            val controller = system.actorOf(JobController.props(modConfig, resources), "launcher-controller")
            controller ! StartJob
          case None =>
            system.terminate().onComplete(_ => sys.exit(1))

        }

      case _ =>
        println(s"Config file at ${path} not found")
        system.terminate()

    }

  }

}
