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

package com.terazyte.flow.task
import java.util.UUID

import com.terazyte.flow.emr.{EMRClusterConfig, EMRClusterConfigParser, EMRResource, EMRTarget}
import com.terazyte.flow.job.{Session, TaskExecResult, TaskExecutor}
import com.terazyte.flow.steps.CreateResourceStep

import scala.util.Try

case class ExecCreateResource(taskDef: CreateResourceStep) extends TaskExecutor[CreateResourceStep] {

  override def execute(session: Session): Either[Throwable, TaskExecResult] = {
    taskDef.resourceDef match {
      case r: EMRResource =>
        val emrTarget = new EMRTarget(r.alias, r.profile, "", r.region, r.keyFile)
        EMRClusterConfigParser.fromFile(r.configPath).flatMap { c =>
          Try(emrTarget.createCluster(c)).toEither.map(_ => TaskExecResult.success(taskDef, UUID.randomUUID().toString))
        }
    }

  }
}
