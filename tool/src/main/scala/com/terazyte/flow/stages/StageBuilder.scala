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

package com.terazyte.flow.job.stages

import akka.actor.ActorContext
import com.terazyte.flow.job.Task
import com.terazyte.flow.parser.LaunchJobConfig

import scala.collection.immutable.Queue

trait StageBuilder {

  def build(context: ActorContext, config: LaunchJobConfig): Queue[Task]

}
