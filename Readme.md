# KMQTT

[![Release](https://jitpack.io/v/davidepianca98/KMQTT.svg)](https://jitpack.io/#davidepianca98/KMQTT)

**KMQTT** is a Kotlin Multiplatform MQTT 3.1.1/5.0 Broker, with the objective of targeting the most possible build targets.

## Features
:heavy_multiplication_x: = TODO
:heavy_check_mark: = Supported
:heavy_plus_sign: = Experimental

| Platform    | MQTT 3.1.1               | MQTT 5.0           | Clustering        | TCP                | TLS                | UDP               | Websocket                |
| :---:       | :---:                    |  :---:             | :---:             | :---:              | :---:              | :---:             | :---:                    |
| JVM         | :heavy_check_mark:       | :heavy_check_mark: | :heavy_plus_sign: | :heavy_check_mark: | :heavy_check_mark: | :heavy_plus_sign: | :heavy_multiplication_x: |
| Windows X64 | :heavy_check_mark:       | :heavy_check_mark: | :heavy_plus_sign: | :heavy_check_mark: | :heavy_check_mark: | :heavy_plus_sign: | :heavy_multiplication_x: |
| Linux X64   | :heavy_check_mark:       | :heavy_check_mark: | :heavy_plus_sign: | :heavy_check_mark: | :heavy_check_mark: | :heavy_plus_sign: | :heavy_multiplication_x: |
| Linux ARM32 | :heavy_check_mark:       | :heavy_check_mark: | :heavy_plus_sign: | :heavy_check_mark: | :heavy_check_mark: | :heavy_plus_sign: | :heavy_multiplication_x: |

## Getting Started

### Executables
You can download the executables for your platform under the release tab

#### Program Arguments
| Argument          | Default Value | Description                                                                                                             |
| :---:             | :---:         | :---:                                                                                                                   |
| -h                | 127.0.0.1     | Interface address to bind the server to                                                                                 |
| -p                | 1883          | Server port to listen to                                                                                                |
| --max-connections | 128           | The maximum number of TCP connections to support                                                                        |
| --key-store       | null          | The path to the PKCS12 keystore containing the private key and the certificate for TLS, if null TLS is disabled         |
| --key-store-psw   | null          | The password of the PKCS12 keystore indicated in --key-store, if the keystore has no password set, don't set the option |

### Library

#### Gradle Maven

##### Global dependencies
```gradle
repositories {
    jcenter()
    maven { url "https://jitpack.io" }
}
dependencies {
    implementation 'com.github.davidepianca98.KMQTT:KMQTT-jvm:0.2.7'
}
```

Replace jvm with linuxx64 or linuxarm32hfp based on the target. mingwx64 target is not yet available through JitPack.

##### Kotlin Multiplatform plugin
On the Kotlin Multiplatform plugin you only need to require the dependency on the common source set and the platform specific parts will automatically be required
```gradle
repositories {
    jcenter()
    maven { url "https://jitpack.io" }
}

kotlin {
    jvm()
    mingwX64("mingw")
    sourceSets {
        commonMain {
            dependencies {
                implementation 'com.github.davidepianca98:KMQTT:0.2.7'
            }
        }
    }
}
```

#### Quick start code example
This code starts the MQTT broker on port 1883 without TLS encryption. You can play with Broker() constructor parameters to set the various settings
```kotlin
fun main() {
    Broker().listen()
}
```

#### TLS code example
The keystore must be in PKCS12 format, the keystore password can be null
```kotlin
fun main() {
    val broker = Broker(
        tlsSettings = TLSSettings(keyStoreFilePath = "keyStore.p12", keyStorePassword = "password"),
        port = 8883
    ).listen()
    broker.listen()
}
```

#### Authentication code example
```kotlin
fun main() {
    val broker = Broker(authentication = object : Authentication {
        override fun authenticate(username: String?, password: UByteArray?): Boolean {
            // TODO Implement your authentication method    
            return username == "user" && password?.toByteArray()?.decodeToString() == "pass"
        }
    })
    broker.listen()
}
```

#### Message interceptor code example
```kotlin
fun main() {
    val broker = Broker(packetInterceptor = object : PacketInterceptor {
        override fun packetReceived(packet: MQTTPacket) {
            when(packet) {
                is MQTTConnect -> println(packet.protocolName)
                is MQTTPublish -> println(packet.topicName)
            }
        }
    })
    broker.listen()
}
```

#### Internal publish code example
```kotlin
fun main() {
    val broker = Broker()
    broker.publish(
        retain = false,
        topicName = "test/",
        qos = Qos.AT_MOST_ONCE,
        properties = MQTTProperties(),
        "testPayload".toByteArray().toUByteArray()
    )
    broker.listen()
}
```
