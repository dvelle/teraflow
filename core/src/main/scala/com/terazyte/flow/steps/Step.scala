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

package com.terazyte.flow.steps

import akka.actor.{ActorContext, Props}
import com.terazyte.flow.job.{Stage, Task, TaskDef}
import net.jcazevedo.moultingyaml.YamlValue

trait Step[T <: TaskDef] {

  val id: String

  def props(step: T): Props

  def parseStep(value: YamlValue): T

}

//trait ExecutableStep {
//
//  def buildTask(context: ActorContext): Task
//
//}
