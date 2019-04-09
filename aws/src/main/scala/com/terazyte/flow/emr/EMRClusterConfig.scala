/*
 * Copyright 2019 Gigahex
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

package com.terazyte.flow.emr

import com.terazyte.flow.steps.ResourceDef
import net.jcazevedo.moultingyaml.DefaultYamlProtocol

import scala.io.Source
import scala.util.Try

case class EMRResource(alias: String,
                       configPath: String,
                       profile: Option[String],
                       keyFile: Option[String],
                       region: Option[String])
    extends ResourceDef

case class EMRClusterConfig(name: String,
                            logUri: String,
                            releaseLabel: String,
                            instances: EMRInstance,
                            applications: Seq[EMRApplication],
                            visibleToAllUsers: Option[Boolean],
                            jobFlowRole: String,
                            serviceRole: String,
                            tags: Seq[EMRTag],
                            configurations: Seq[EMRConfigClassified],
                            bootstrapActions: Seq[BootstrapAction])
    extends ResourceDef

case class EMRInstance(keepJobFlowAliveWhenNoSteps: Option[Boolean],
                       terminationProtected: Option[Boolean],
                       ec2KeyName: Option[String],
                       ec2SubnetId: String,
                       emrManagedMasterSecurityGroup: Option[String],
                       emrManagedSlaveSecurityGroup: Option[String],
                       serviceAccessSecurityGroup: Option[String],
                       additionalMasterSecurityGroups: Option[Seq[String]],
                       additionalSlaveSecurityGroups: Option[Seq[String]],
                       instanceGroups: Seq[EMRInstanceGroup])

case class EMRTag(key: String, value: String)

case class EMRInstanceGroup(instanceCount: Int,
                            name: String,
                            instanceRole: String,
                            market: String,
                            instanceType: String,
                            ebsConfiguration: EMREbsConfiguration)

case class EMRApplication(name: String)

case class EMREbsConfiguration(ebsBlockDeviceConfigs: Seq[EMREbsBlockDeviceConfig])

case class EMREbsBlockDeviceConfig(volumeSpecification: VolumeSpec, volumesPerInstance: Int)
case class VolumeSpec(sizeInGB: Int, volumeType: String)

case class EMRConfigClassified(classification: String, properties: Map[String, String])
case class BootstrapAction(name: String, scriptBootstrapAction: ScriptBootstrapAction)
case class ScriptBootstrapAction(path: String, args: Seq[String])

trait EMRConfigYamlProtocol extends DefaultYamlProtocol {
  implicit val scriptBootstrapActionFormat = yamlFormat2(ScriptBootstrapAction)
  implicit val bootstrapActionFormat       = yamlFormat2(BootstrapAction)
  implicit val EMRConfigClassifiedFormat   = yamlFormat2(EMRConfigClassified)
  implicit val emrTagFormat                = yamlFormat2(EMRTag)
  implicit val emrAppFormat                = yamlFormat1(EMRApplication)
  implicit val volSpecFormat               = yamlFormat2(VolumeSpec)
  implicit val ebsBlockDeviceConfigFormat  = yamlFormat2(EMREbsBlockDeviceConfig)
  implicit val ebsConfigFormat             = yamlFormat1(EMREbsConfiguration)
  implicit val instanceGroupFormat         = yamlFormat6(EMRInstanceGroup)
  implicit val emrInstanceFormat           = yamlFormat10(EMRInstance)
  implicit val EMRClusterConfigFormat      = yamlFormat11(EMRClusterConfig)

}

object EMRClusterConfigParser extends EMRConfigYamlProtocol {
  import net.jcazevedo.moultingyaml._

  def fromFile(path: String): Either[Throwable, EMRClusterConfig] = {
    val modifiedYaml = Source
      .fromFile(path)
      .getLines()
      .map { line =>
        if (line.contains(":")) {
          val parts      = line.split(":")
          val part       = parts(0)
          var upperCased = false
          val camelCased = part.map { x =>
            if (!upperCased && x.isUpper) {
              upperCased = true
              x.toLower
            } else x
          }

          camelCased + ":" + { if (parts.length > 1) parts.drop(1).mkString(":") else "" }

        } else line
      }
      .mkString("\n")

    Try(modifiedYaml.parseYaml.convertTo[EMRClusterConfig]).toEither

  }

}
