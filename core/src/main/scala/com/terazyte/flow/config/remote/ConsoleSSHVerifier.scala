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

package com.terazyte.flow.config.remote

import java.io.{Console, File, IOException}
import java.security.PublicKey

import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.{ConsoleKnownHostsVerifier, OpenSSHKnownHosts}

class ConsoleSSHVerifier(keyFile: File, console: Console) extends ConsoleKnownHostsVerifier(keyFile, console) {

  override protected def hostKeyUnverifiableAction(hostname: String, key: PublicKey): Boolean = {

    try {
      entries.add(new OpenSSHKnownHosts.HostEntry(null, hostname, KeyType.fromKey(key), key))
      write()

    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }
    return true
  }

}
