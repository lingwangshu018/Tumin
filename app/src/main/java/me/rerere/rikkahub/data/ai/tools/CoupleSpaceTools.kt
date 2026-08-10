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
        ?: error("当前调用没有角色身份，不能访问情侣空间")
    val relationship = repository.relationship.first()
        ?: error("还没有建立情侣空间")
    if (relationship.assistantId != callerAssistantId) {
        error("当前角色不是这个情侣空间绑定的角色")
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
        Read the current assistant's bound couple-space / QQ-space timeline and recent comments.
        Use this when the user asks you to look at their QQ space, check a post, inspect recent couple-space activity,
        or before commenting on a specific post. Returns real post IDs; never pretend you read the space without calling this tool.
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
            .getOrElse { return@Tool toolError(it.message ?: "无法读取情侣空间") }
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
        Publish a real QQ-space style post as the current assistant into the couple space bound to this assistant.
        Use it when the user asks you to post/share something there, or when you naturally and intentionally choose to post.
        The database is actually changed; do not claim you posted unless this tool succeeds.
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
            .getOrElse { return@Tool toolError(it.message ?: "无法访问情侣空间") }
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
        Add a real comment as the current assistant to an existing QQ-space post in the bound couple space.
        Call read_couple_space first if you do not know the exact post_id. Never invent a post ID and never claim a comment was posted unless this tool succeeds.
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
            .getOrElse { return@Tool toolError(it.message ?: "无法访问情侣空间") }
        val postId = input.jsonObject["post_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val content = input.jsonObject["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (postId.isBlank() || content.isBlank()) return@Tool toolError("post_id 和评论正文都不能为空")
        val post = repository.posts(relationship.id).first().firstOrNull { it.id == postId }
            ?: return@Tool toolError("没有找到这条动态，请先重新读取空间")
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
