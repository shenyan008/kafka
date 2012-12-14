package kafka.api

import java.nio.ByteBuffer

import kafka.api.ApiUtils._
import kafka.common.TopicAndPartition
import kafka.utils.Logging

object OffsetCommitRequest extends Logging {
  val CurrentVersion = 1.shortValue()
  val DefaultClientId = "default"

  def readFrom(buffer: ByteBuffer): OffsetCommitRequest = {
    // Read values from the envelope
    val versionId = buffer.getShort
    val correlationId = buffer.getInt
    val clientId = readShortString(buffer)

    // Read the OffsetRequest 
    val consumerGroupId = readShortString(buffer)
    val topicCount = buffer.getInt
    val pairs = (1 to topicCount).flatMap(_ => {
      val topic = readShortString(buffer)
      val partitionCount = buffer.getInt
      (1 to partitionCount).map(_ => {
        val partitionId = buffer.getInt
        val offset = buffer.getLong
        (TopicAndPartition(topic, partitionId), offset)
      })
    })
    OffsetCommitRequest(consumerGroupId, Map(pairs:_*), versionId, correlationId, clientId)
  }
}

case class OffsetCommitRequest(groupId: String,
                               requestInfo: Map[TopicAndPartition, Long],
                               versionId: Short = OffsetCommitRequest.CurrentVersion,
                               correlationId: Int = 0,
                               clientId: String = OffsetCommitRequest.DefaultClientId)
    extends RequestOrResponse(Some(RequestKeys.OffsetCommitKey)) {

  lazy val requestInfoGroupedByTopic = requestInfo.groupBy(_._1.topic)
  
  def writeTo(buffer: ByteBuffer) {
    // Write envelope
    buffer.putShort(versionId)
    buffer.putInt(correlationId)
    writeShortString(buffer, clientId)

    // Write OffsetCommitRequest
    writeShortString(buffer, groupId)             // consumer group
    buffer.putInt(requestInfoGroupedByTopic.size) // number of topics
    requestInfoGroupedByTopic.foreach( t1 => { // topic -> Map[TopicAndPartition, Long]
      writeShortString(buffer, t1._1) // topic
      buffer.putInt(t1._2.size)       // number of partitions for this topic
      t1._2.foreach( t2 => {
        buffer.putInt(t2._1.partition)  // partition
        buffer.putLong(t2._2)           // offset
      })
    })
  }

  override def sizeInBytes =
    2 + /* versionId */
    4 + /* correlationId */
    shortStringLength(clientId) +
    shortStringLength(groupId) + 
    4 + /* topic count */
    requestInfoGroupedByTopic.foldLeft(0)((count, topicAndOffsets) => {
      val (topic, offsets) = topicAndOffsets
      count +
      shortStringLength(topic) + /* topic */
      4 + /* number of partitions */
      offsets.size * (
        4 + /* partition */
        8 /* offset */
      )
    })
}
