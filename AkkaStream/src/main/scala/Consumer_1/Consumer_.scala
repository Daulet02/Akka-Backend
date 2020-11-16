package Consumer_1

import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.{ConsumerSettings, ProducerMessage, Subscriptions}
import akka.stream.scaladsl.Sink
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer


object Consumer_ {
  implicit val system = ActorSystem("QuickStart")
  implicit val ec = system.dispatcher

  val config = system.settings.config.getConfig("akka.kafka.producer")
  val server = system.settings.config.getString("akka.kafka.producer.kafka-clients.server")
  val topic1 = system.settings.config.getString("akka.kafka.producer.kafka-clients.topic")


  val bootstrapServers = system.settings.config.getString("akka.kafka.producer.kafka-clients.server")

  val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(bootstrapServers)
    .withGroupId("Group1")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    .withClientId("externalOffsetStorage")

  val db = new OffsetStore
  db.loadOffset().map { fromOffset =>
    Consumer
      .plainSource(
        consumerSettings,
        Subscriptions.assignmentWithOffset(
          new TopicPartition(topic1, 0) -> fromOffset
        )
      )
      .mapAsync(1)(db.businessLogicAndStoreOffset)
      .toMat(Sink.seq)(DrainingControl.apply)
      .run()
  }

}
