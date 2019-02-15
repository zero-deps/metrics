package .stats

import akka.actor.{ActorLogging, Actor, ActorRef, Props, Stash}

object WsPub {
  def props: Props = Props(new WsPub)
}

class WsPub extends Actor with Stash with ActorLogging {
  import context.system

  override def preStart: Unit = system.eventStream.subscribe(self, classOf[Msg])
  override def postStop: Unit = system.eventStream.unsubscribe(self)

  def receive: Receive = {
    case _: Msg => stash()
    case stageActor: ActorRef =>
      log.debug("got stage actor")
      unstashAll()
      context.become(ready(stageActor))
  }

  def ready(stageActor: ActorRef): Receive = {
    case msg: Msg => stageActor ! msg
  }
}
