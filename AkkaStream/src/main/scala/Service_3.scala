import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.{ConsumerSettings, ProducerMessage, Subscriptions}
import akka.stream.scaladsl.Sink
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

object Service_3 {
  implicit val system = ActorSystem("QuickStart")
  implicit val ec = system.dispatcher

  val config = system.settings.config.getConfig("akka.kafka.producer")
  val server = system.settings.config.getString("akka.kafka.producer.kafka-clients.server")
  val bootstrapServers = system.settings.config.getString("akka.kafka.producer.kafka-clients.server")


  val consumerSettings = ConsumerSettings(config, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServers)
    .withGroupId("MainGroup")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")


}
