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

case class CLIConfig(configPath: String = new File(".").getCanonicalPath() + s"${OSHelper.slash}.launch.yml",
                     stages: Array[String] = Array(),
                     version: Boolean = false,
                     verbose: Boolean = false)

object CLIConfig {
  val program = "launch"
  val cliParser = new scopt.OptionParser[CLIConfig](program) {
    head(program, "0.1.0")

    opt[String]('c', "config")
      .valueName("<launch-config>")
      .text("Path of the configuration file")
      .action((path, cliConfig) => cliConfig.copy(configPath = path))

    opt[String]('s', "stages")
      .valueName("<stage names>")
      .text("Stages to run")
      .action((stages, cliConfig) => {
        if (stages.nonEmpty) cliConfig.copy(stages = stages.split(",")) else cliConfig
      })

    opt[Unit]('v', "version").action((_, c) => c.copy(version = true)).text("Show current version")

    opt[Unit]('x', "verbose").action((_, c) => c.copy(verbose = true)).text("verbose is a flag")
  }

}

object OSHelper {

  lazy val slash: String = System.getProperty("os.name") match {
    case x if x.startsWith("Windows") => "\\"
    case _                            => "/"
  }

}
