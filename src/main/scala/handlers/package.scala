package .stats
package handlers

import scala.util.{ Failure, Success, Try }
import .kvs.handle.`package`.En
import .kvs.Kvs
import .kvs.handle.EnHandler

object api {
  val STATS_FID = "stats"

  trait UdpHandler {
    val udpMessage: PartialFunction[String, Try[Data]]
  }

  trait SocketHandler {
    val socketMsg: PartialFunction[Data, Try[String]]
  }

  trait KvsHandler {
    def saveToKvs(kvs: Kvs): PartialFunction[Data, Try[En[Data]]]
    def getFromKvs(kvs: Kvs): PartialFunction[(Option[Int], String), Try[List[Data]]]
  }

  trait ByEnHandler[T <: Data] {
    def serialize(data: T): String
    def deSerialize(str: String): T

    lazy val handler: EnHandler[T] = EnHandler.by[T, String](serialize _)(deSerialize _)
  }

  trait KvsHandlerTyped[T <: Data] extends KvsHandler {
    val TYPE_ALIAS: String
    protected lazy val FID = s"$STATS_FID::$TYPE_ALIAS"

    val handler: EnHandler[T]

    protected def kvsFilter(data: Data): Option[T]

    protected def entry(data: T) = En[T](FID, java.util.UUID.randomUUID.toString, data)

    override def saveToKvs(kvs: Kvs) = {
      case data: Data if (kvsFilter(data).isDefined) =>
        val value = entry(kvsFilter(data).get)

        val res = kvs.add(value)(handler)
        res fold (
          { l =>
            Failure(new Exception(l))
          },
          { r =>
            Success(En[Data](r.fid, r.id, r.prev, r.next, r.data))
          })
    }

    override def getFromKvs(kvs: Kvs) = {
      case (count, TYPE_ALIAS) =>
        kvs.entries(FID, None, count)(handler) fold (
          {error =>
            if (error eq "not_found") Success(List.empty)
            else Failure(new Exception(error))},
          entries => Success(entries map { _.data }))
    }
  }
}