package com.terazyte.flow.job.stages

import akka.actor.ActorContext
import com.terazyte.flow.job.{Install, Task}
import com.terazyte.flow.parser.LaunchJobConfig
import com.terazyte.flow.steps.{CopyStep, RemoteCmdStep}
import com.terazyte.flow.task.{ExecRemoteCmd, ExecRemoteCopy}

import scala.collection.immutable.Queue

//object InstallStage extends StageBuilder {
//
//  override def build(context: ActorContext, config: LaunchJobConfig): Queue[Task] = {
//    val tasks = config.install.steps.map {
//      case x: CopyStep      => ExecRemoteCopy.build(Install, context, x)
//      case x: RemoteCmdStep => ExecRemoteCmd.build(Install, context, x)
//    }
//    Queue(tasks: _*)
//  }
//
//}
