package com.terazyte.flow.docker

import com.terazyte.flow.config.Resource
import net.jcazevedo.moultingyaml.DefaultYamlProtocol._
import net.jcazevedo.moultingyaml.YamlValue

trait DockerSupport {

  implicit val dockerFormat = yamlFormat2(DockerConfig)

}

object DockerYamlParser extends Resource[DockerContainer] {

  override val id: String       = "docker"
  implicit val remoteHostFormat = yamlFormat3(DockerContainer.apply)
  override def parse(v: YamlValue): DockerContainer = {
    v.convertTo[DockerContainer]
  }
}
