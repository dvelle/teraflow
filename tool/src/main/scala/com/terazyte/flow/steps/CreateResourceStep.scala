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
import com.terazyte.flow.emr.{EMRResource, EMRSupport, EMRTargetYamlParser}
import com.terazyte.flow.job.{Stage, Task, TaskDef}
import com.terazyte.flow.task.ExecCreateResource
import net.jcazevedo.moultingyaml.{DefaultYamlProtocol, YamlString, YamlValue}

case class CreateResourceStep(resource: String, resourceDef: ResourceDef)
    extends TaskDef(name = s"Create ${resource}")
    with ExecutableStep {

  override def buildTask(context: ActorContext, stage: Stage): Task = {
    val actor = context.actorOf(CreateResourceStep.props(this))
    Task(stage, this, actor)

  }

}

object CreateResourceStep extends Step[CreateResourceStep] with EMRSupport {

  override val id: String                             = "create"
  override def props(step: CreateResourceStep): Props = Props(new ExecCreateResource(step))
  override def parseStep(value: YamlValue): CreateResourceStep = {
    val resDef = value.asYamlObject.fields.map {
      case (YamlString(EMRTargetYamlParser.id), v) => v.convertTo[EMRResource]
    }.head

    CreateResourceStep(EMRTargetYamlParser.id, resDef)
  }
}
