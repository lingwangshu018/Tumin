package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.CoupleRepository

private suspend fun resolveCoupleRelationship(
    repository: CoupleRepository,
    invocationContext: ToolInvocationContext,
): Result<me.rerere.rikkahub.data.db.entity.CoupleRelationshipEntity> = runCatching {
    val callerAssistantId = invocationContext.callerAssistantId
        ?: error("当前调用没有角色身份，不能访问兔眠空间")
    val relationship = repository.relationship.first()
        ?: error("还没有建立情侣空间")
    if (relationship.assistantId != callerAssistantId) {
        error("当前角色不是这个兔眠空间绑定的角色")
    }
    relationship
}

private fun toolError(message: String): List<UIMessagePart> = listOf(
    UIMessagePart.Text(
        buildJsonObject {
            put("success", false)
            put("error", message)
        }.toString()
    )
)

fun readCoupleSpaceTool(
    repository: CoupleRepository,
    invocationContext: ToolInvocationContext,
) = Tool(
    name = "read_couple_space",
    description = """
        【兔眠空间专用读取工具】当用户说“看看兔眠空间”“去空间看看”“看看我刚发的动态”“读最新评论”时，必须优先使用本工具。
        Read the current assistant's bound Rabbit Sleep Space timeline and recent comments.
        Returns real post IDs and comments. Never pretend that you checked the space without a successful tool result.
        This tool is for OrangeChat Rabbit Sleep Space data; do NOT use HTML/image/plugin tools for these requests.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum number of recent posts to return, default 8, range 1-20")
                })
            },
            required = emptyList(),
        )
    },
    execute = { input ->
        val relationship = resolveCoupleRelationship(repository, invocationContext)
            .getOrElse { return@Tool toolError(it.message ?: "无法读取兔眠空间") }
        val limit = input.jsonObject["limit"]?.jsonPrimitive?.contentOrNull
            ?.toIntOrNull()?.coerceIn(1, 20) ?: 8
        val posts = repository.posts(relationship.id).first().take(limit)
        val comments = repository.comments(relationship.id).first()
        val payload = buildJsonObject {
            put("success", true)
            put("relationship_id", relationship.id)
            put("posts", buildJsonArray {
                posts.forEach { post ->
                    add(buildJsonObject {
                        put("post_id", post.id)
                        put("author", post.author)
                        put("content", post.content)
                        put("has_images", !post.imageUri.isNullOrBlank())
                        put("liked", post.liked)
                        put("created_at", post.createdAt)
                        put("comments", buildJsonArray {
                            comments.filter { it.postId == post.id }.forEach { comment ->
                                add(buildJsonObject {
                                    put("comment_id", comment.id)
                                    put("author", comment.author)
                                    put("content", comment.content)
                                    put("created_at", comment.createdAt)
                                })
                            }
                        })
                    })
                }
            })
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

fun postCoupleSpaceTool(
    repository: CoupleRepository,
    invocationContext: ToolInvocationContext,
) = Tool(
    name = "post_couple_space",
    description = """
        【兔眠空间专用发动态工具】当用户说“发条兔眠空间”“去空间发动态”“你也发一条”“更新一下兔眠空间”时，使用本工具真正写入动态。
        Publish a real post as the current assistant into the bound Rabbit Sleep Space.
        The database is actually changed. Never claim a post was published unless this tool returns success.
        Do NOT substitute HTML/image/plugin tools for a Rabbit Sleep Space post request.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "The final post body to publish, in your normal character voice")
                })
            },
            required = listOf("content"),
        )
    },
    execute = { input ->
        val relationship = resolveCoupleRelationship(repository, invocationContext)
            .getOrElse { return@Tool toolError(it.message ?: "无法访问兔眠空间") }
        val content = input.jsonObject["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (content.isBlank()) return@Tool toolError("动态正文不能为空")
        val post = repository.addPost(
            relationshipId = relationship.id,
            author = "assistant",
            content = content.take(2000),
        )
        listOf(UIMessagePart.Text(buildJsonObject {
            put("success", true)
            put("post_id", post.id)
            put("content", post.content)
        }.toString()))
    },
)

fun commentCoupleSpaceTool(
    repository: CoupleRepository,
    invocationContext: ToolInvocationContext,
) = Tool(
    name = "comment_couple_space",
    description = """
        【兔眠空间专用评论工具】当用户说“评论我刚发的动态”“给这条动态留言”“去兔眠空间评论一下”时，使用本工具真正写入评论。
        Add a real comment as the current assistant to an existing Rabbit Sleep Space post.
        Call read_couple_space first if you do not know the exact post_id. Never invent a post ID and never claim success without a successful tool result.
        Do NOT use HTML/image/plugin tools for Rabbit Sleep Space comments.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("post_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact post_id returned by read_couple_space")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "The final comment text to publish")
                })
            },
            required = listOf("post_id", "content"),
        )
    },
    execute = { input ->
        val relationship = resolveCoupleRelationship(repository, invocationContext)
            .getOrElse { return@Tool toolError(it.message ?: "无法访问兔眠空间") }
        val postId = input.jsonObject["post_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val content = input.jsonObject["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (postId.isBlank() || content.isBlank()) return@Tool toolError("post_id 和评论正文都不能为空")
        val post = repository.posts(relationship.id).first().firstOrNull { it.id == postId }
            ?: return@Tool toolError("没有找到这条动态，请先重新读取兔眠空间")
        val comment = repository.addComment(
            relationshipId = relationship.id,
            postId = post.id,
            author = "assistant",
            content = content.take(1000),
        )
        listOf(UIMessagePart.Text(buildJsonObject {
            put("success", true)
            put("post_id", post.id)
            put("comment_id", comment.id)
            put("content", comment.content)
        }.toString()))
    },
)

fun deleteCoupleSpacePostTool(
    repository: CoupleRepository,
    invocationContext: ToolInvocationContext,
) = Tool(
    name = "delete_couple_space_post",
    description = """
        【兔眠空间专用删除动态工具】删除兔眠空间中的一条真实动态，并同时删除该动态下的评论。
        Use only when the user clearly asks to delete a post. If post_id is unknown, call read_couple_space first.
        Never invent a post_id and never delete a post without a clear user request.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("post_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact post_id returned by read_couple_space")
                })
            },
            required = listOf("post_id"),
        )
    },
    execute = { input ->
        val relationship = resolveCoupleRelationship(repository, invocationContext)
            .getOrElse { return@Tool toolError(it.message ?: "无法访问兔眠空间") }
        val postId = input.jsonObject["post_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (postId.isBlank()) return@Tool toolError("post_id 不能为空")
        val post = repository.posts(relationship.id).first().firstOrNull { it.id == postId }
            ?: return@Tool toolError("没有找到这条动态，请先重新读取兔眠空间")
        repository.deletePost(post)
        listOf(UIMessagePart.Text(buildJsonObject {
            put("success", true)
            put("post_id", post.id)
            put("deleted", true)
        }.toString()))
    },
)
