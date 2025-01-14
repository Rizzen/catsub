import derevo.derive
import derevo.circe._
import io.circe.Json

package object models {
  @derive(codec) case class Chat(id: Long)
  @derive(codec) case class Sticker(set_name: Option[String])
  @derive(codec) case class Message(
    message_id: Long,
    date: Long,
    chat: Chat,
    from: Chat,
    reply_to_message: Option[Message],
    text: Option[String],
    caption: Option[String],
    sticker: Option[Sticker],
    entities: Option[List[MessageEntity]],
    photo: Option[Json]
  ) {
    def textOrCaption: Option[String] = text.orElse(caption)
  }
  @derive(codec) case class Update(update_id: Long, message: Option[Message], edited_message: Option[Message])
  @derive(codec) case class GetUpdatesResponse(result: Vector[Update])
  @derive(codec) case class SendMessageResponse(result: Message)
  @derive(codec) case class MessageEntity(`type`: String)
}
