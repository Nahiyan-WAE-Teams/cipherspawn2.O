package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.AppNotificationManager
import com.example.core.NumberFormatter
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.local.CachedChannelEntity
import com.example.data.local.CachedSnapshotEntity
import com.example.data.local.CachedVideoEntity
import com.example.data.local.TubePulseDao
import com.example.data.local.TubePulseDatabase
import com.example.data.model.AlertEvent
import com.example.data.model.AlertMetricType
import com.example.data.model.ChannelSnapshot
import com.example.data.model.TrackedChannel
import com.example.data.model.UserProfile
import com.example.data.model.YouTubeChannelSearchResult
import com.example.data.remote.PublicYouTubeExtractor
import com.example.widget.TubePulseGlanceWidget
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TubePulseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val dao: TubePulseDao,
    private val publicExtractor: PublicYouTubeExtractor,
    private val notificationManager: AppNotificationManager,
    private val appContext: Context,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
        dao = TubePulseDatabase.getInstance(context.applicationContext).tubePulseDao(),
        publicExtractor = PublicYouTubeExtractor(),
        notificationManager = AppNotificationManager(context.applicationContext),
        appContext = context.applicationContext,
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeFirestoreChannels(userId: String): Flow<List<TrackedChannel>> = flow {
        val path = "users/$userId/channels"
        emitAll(
            db.collection("users").document(userId).collection("channels")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TrackedChannel::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .onEach { cloudChannels ->
                    syncCloudChannelsToRoom(userId, cloudChannels)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCachedChannels(userId: String): Flow<List<CachedChannelEntity>> =
        dao.observeChannels(userId)

    fun observeCachedChannel(userId: String, channelId: String): Flow<CachedChannelEntity?> =
        dao.observeChannel(userId, channelId)

    fun observeCachedVideos(userId: String, channelId: String): Flow<List<CachedVideoEntity>> =
        dao.observeVideosForChannel(userId, channelId)

    fun observeCachedSnapshots(userId: String, channelId: String): Flow<List<CachedSnapshotEntity>> =
        dao.observeSnapshots(userId, channelId)

    fun observeFirestoreSnapshots(userId: String, channelId: String): Flow<List<ChannelSnapshot>> = flow {
        val path = "users/$userId/channels/$channelId/snapshots"
        emitAll(
            db.collection("users").document(userId)
                .collection("channels").document(channelId)
                .collection("snapshots")
                .orderBy("recordedAt", Query.Direction.ASCENDING)
                .limitToLast(60)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChannelSnapshot::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .onEach { snaps ->
                    val entities = snaps.map { snap ->
                        CachedSnapshotEntity(
                            snapshotId = snap.id,
                            channelId = snap.channelId,
                            userId = snap.userId,
                            subscriberCount = snap.subscriberCount,
                            viewCount = snap.viewCount,
                            videoCount = snap.videoCount,
                            likeCount = snap.likeCount,
                            recordedAtMillis = snap.recordedAt?.toDate()?.time
                                ?: System.currentTimeMillis(),
                        )
                    }
                    if (entities.isNotEmpty()) {
                        dao.upsertSnapshots(entities)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAlerts(userId: String): Flow<List<AlertEvent>> = flow {
        val path = "users/$userId/alerts"
        emitAll(
            db.collection("users").document(userId).collection("alerts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AlertEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val docPath = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val safeName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Creator Studio").take(80)
            val safeEmail = user.email?.takeIf { it.isNotBlank() }?.take(160)
            val safePhoto = user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.take(500)

            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "syncIntervalSeconds" to 15L,
                    "notificationsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSyncPreferences(
        syncIntervalSeconds: Long,
        notificationsEnabled: Boolean,
    ): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val boundedInterval = syncIntervalSeconds.coerceIn(10L, 3600L)
            db.collection("users").document(uid).update(
                mapOf(
                    "syncIntervalSeconds" to boundedInterval,
                    "notificationsEnabled" to notificationsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun searchPublicChannels(rawQuery: String): Result<List<YouTubeChannelSearchResult>> =
        try {
            val query = rawQuery.trim()
            if (query.isBlank()) return Result.success(emptyList())
            Result.success(publicExtractor.searchChannels(query, 8).map { it.toSearchResult() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun trackAndSyncChannel(candidate: YouTubeChannelSearchResult): Result<TrackedChannel> {
        var docPath = "users/unauthenticated/channels/${candidate.channelId}"
        return try {
            val uid = requireUserId()
            require(isValidFirestoreId(candidate.channelId)) { "Invalid YouTube Channel ID" }
            docPath = "users/$uid/channels/${candidate.channelId}"

            val existingLocal = dao.getChannelsSync(uid)
            val shouldPinByDefault = existingLocal.none { it.pinnedToWidget }

            val totalRecentLikes = existingLocal.firstOrNull { it.channelId == candidate.channelId }?.latestLikeCount ?: 0L

            val docRef = db.collection("users").document(uid)
                .collection("channels").document(candidate.channelId)
            val existingSnap = docRef.get().await()

            val safeTitle = candidate.title.ifBlank { "YouTube Channel" }.take(120)
            val safeHandle = candidate.handle.takeIf { it.isNotBlank() }?.take(80)
            val safeDesc = candidate.description.takeIf { it.isNotBlank() }?.take(500)
            val safeThumb = candidate.thumbnailUrl.takeIf { it.isNotBlank() }?.take(500)
            val safeUploads = candidate.uploadsPlaylistId.takeIf { it.isNotBlank() }?.take(80)

            val defaultMilestone = computeSmartMilestoneStep(candidate.subscriberCount)

            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "channelId" to candidate.channelId,
                    "title" to safeTitle,
                    "handle" to safeHandle,
                    "description" to safeDesc,
                    "thumbnailUrl" to safeThumb,
                    "subscriberCount" to candidate.subscriberCount.coerceAtLeast(0L),
                    "viewCount" to candidate.viewCount.coerceAtLeast(0L),
                    "videoCount" to candidate.videoCount.coerceAtLeast(0L),
                    "latestLikeCount" to totalRecentLikes.coerceAtLeast(0L),
                    "milestoneStep" to defaultMilestone,
                    "alertsEnabled" to true,
                    "pinnedToWidget" to shouldPinByDefault,
                    "hiddenSubscriberCount" to candidate.hiddenSubscriberCount,
                    "uploadsPlaylistId" to safeUploads,
                    "lastSyncedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "title" to safeTitle,
                    "handle" to safeHandle,
                    "description" to safeDesc,
                    "thumbnailUrl" to safeThumb,
                    "subscriberCount" to candidate.subscriberCount.coerceAtLeast(0L),
                    "viewCount" to candidate.viewCount.coerceAtLeast(0L),
                    "videoCount" to candidate.videoCount.coerceAtLeast(0L),
                    "latestLikeCount" to totalRecentLikes.coerceAtLeast(0L),
                    "hiddenSubscriberCount" to candidate.hiddenSubscriberCount,
                    "uploadsPlaylistId" to safeUploads,
                    "lastSyncedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.update(updatePayload).await()
            }

            // Record initial telemetry snapshot
            recordSnapshot(
                uid = uid,
                channelId = candidate.channelId,
                subscribers = candidate.subscriberCount,
                views = candidate.viewCount,
                videos = candidate.videoCount,
                likes = totalRecentLikes,
            )

            val cachedEntity = CachedChannelEntity(
                channelId = candidate.channelId,
                userId = uid,
                title = safeTitle,
                handle = safeHandle.orEmpty(),
                description = safeDesc.orEmpty(),
                thumbnailUrl = safeThumb.orEmpty(),
                subscriberCount = candidate.subscriberCount.coerceAtLeast(0L),
                previousSubscriberCount = existingLocal.firstOrNull { it.channelId == candidate.channelId }?.subscriberCount
                    ?: candidate.subscriberCount.coerceAtLeast(0L),
                viewCount = candidate.viewCount.coerceAtLeast(0L),
                previousViewCount = existingLocal.firstOrNull { it.channelId == candidate.channelId }?.viewCount
                    ?: candidate.viewCount.coerceAtLeast(0L),
                videoCount = candidate.videoCount.coerceAtLeast(0L),
                latestLikeCount = totalRecentLikes,
                previousLikeCount = existingLocal.firstOrNull { it.channelId == candidate.channelId }?.latestLikeCount
                    ?: totalRecentLikes,
                milestoneStep = existingSnap.getLong("milestoneStep") ?: defaultMilestone,
                alertsEnabled = existingSnap.getBoolean("alertsEnabled") ?: true,
                pinnedToWidget = existingSnap.getBoolean("pinnedToWidget") ?: shouldPinByDefault,
                hiddenSubscriberCount = candidate.hiddenSubscriberCount,
                uploadsPlaylistId = safeUploads.orEmpty(),
                lastSyncedAtMillis = System.currentTimeMillis(),
                updatedAtMillis = System.currentTimeMillis(),
            )
            dao.upsertChannel(cachedEntity)
            TubePulseGlanceWidget.pushWidgetUpdate(appContext)

            val saved = docRef.get().await().toObject(
                TrackedChannel::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: TrackedChannel(
                id = candidate.channelId,
                userId = uid,
                channelId = candidate.channelId,
                title = safeTitle,
                handle = safeHandle,
                description = safeDesc,
                thumbnailUrl = safeThumb,
                subscriberCount = candidate.subscriberCount,
                viewCount = candidate.viewCount,
                videoCount = candidate.videoCount,
                latestLikeCount = totalRecentLikes,
                milestoneStep = defaultMilestone,
                alertsEnabled = true,
                pinnedToWidget = shouldPinByDefault,
                hiddenSubscriberCount = candidate.hiddenSubscriberCount,
                uploadsPlaylistId = safeUploads,
            )
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncAllTrackedChannels(
        userId: String,
        notificationsEnabled: Boolean = true,
    ): Result<Int> = try {
        val local = dao.getChannelsSync(userId)
        val channels = if (local.isNotEmpty()) local else {
            val cloud = db.collection("users").document(userId).collection("channels")
                .get().await()
                .toObjects(TrackedChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            syncCloudChannelsToRoom(userId, cloud)
            dao.getChannelsSync(userId)
        }
        var count = 0
        channels.forEach { channel ->
            if (syncSingleChannel(userId, channel.channelId, notificationsEnabled).isSuccess) count++
        }
        TubePulseGlanceWidget.pushWidgetUpdate(appContext)
        Result.success(count)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun syncSingleChannel(
        userId: String,
        channelId: String,
        notificationsEnabled: Boolean = true,
    ): Result<Unit> = try {
        val previous = dao.getChannelsSync(userId).firstOrNull { it.channelId == channelId }
            ?: return Result.failure(IllegalStateException("Channel not found in your tracked list"))
        val source = previous.handle.takeIf { it.startsWith("@") }
            ?: "https://www.youtube.com/channel/$channelId"
        val fresh = publicExtractor.fetchChannel(source)
        val newSubs = fresh.subscriberCount.takeIf { it > 0L } ?: previous.subscriberCount
        val newVideos = fresh.videoCount.takeIf { it > 0L } ?: previous.videoCount
        // Public channel extraction does not expose reliable channel-wide views or likes.
        val newViews = previous.viewCount
        val newLikes = previous.latestLikeCount
        val title = fresh.title.ifBlank { previous.title }.take(120)
        val handle = fresh.handle.ifBlank { previous.handle }.take(80)
        val description = fresh.description.ifBlank { previous.description }.take(500)
        val thumbnail = fresh.thumbnailUrl.ifBlank { previous.thumbnailUrl }.take(500)
        val now = System.currentTimeMillis()

        db.collection("users").document(userId).collection("channels").document(channelId)
            .update(
                mapOf(
                    "title" to title,
                    "handle" to handle.takeIf { it.isNotBlank() },
                    "description" to description.takeIf { it.isNotBlank() },
                    "thumbnailUrl" to thumbnail.takeIf { it.isNotBlank() },
                    "subscriberCount" to newSubs,
                    "videoCount" to newVideos,
                    "hiddenSubscriberCount" to fresh.hiddenSubscriberCount,
                    "lastSyncedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
            ).await()

        dao.upsertChannel(
            previous.copy(
                title = title,
                handle = handle,
                description = description,
                thumbnailUrl = thumbnail,
                previousSubscriberCount = previous.subscriberCount,
                subscriberCount = newSubs,
                previousViewCount = previous.viewCount,
                viewCount = newViews,
                videoCount = newVideos,
                previousLikeCount = previous.latestLikeCount,
                latestLikeCount = newLikes,
                hiddenSubscriberCount = fresh.hiddenSubscriberCount,
                lastSyncedAtMillis = now,
                updatedAtMillis = now,
            )
        )
        if (newSubs != previous.subscriberCount || newVideos != previous.videoCount) {
            recordSnapshot(userId, channelId, newSubs, newViews, newVideos, newLikes)
        }
        if (previous.alertsEnabled && notificationsEnabled) {
            checkAndEmitMilestones(userId, previous, newSubs, newVideos, newLikes, title)
        }
        TubePulseGlanceWidget.pushWidgetUpdate(appContext)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "users/$userId/channels/$channelId")
        Result.failure(e)
    }

    private suspend fun recordSnapshot(
        uid: String,
        channelId: String,
        subscribers: Long,
        views: Long,
        videos: Long,
        likes: Long,
    ) {
        val snapId = "snap_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val path = "users/$uid/channels/$channelId/snapshots/$snapId"
        try {
            val payload = mapOf(
                "userId" to uid,
                "channelId" to channelId,
                "subscriberCount" to subscribers.coerceAtLeast(0L),
                "viewCount" to views.coerceAtLeast(0L),
                "videoCount" to videos.coerceAtLeast(0L),
                "likeCount" to likes.coerceAtLeast(0L),
                "recordedAt" to FieldValue.serverTimestamp(),
            )
            db.collection("users").document(uid)
                .collection("channels").document(channelId)
                .collection("snapshots").document(snapId)
                .set(payload)
                .await()

            dao.upsertSnapshot(
                CachedSnapshotEntity(
                    snapshotId = snapId,
                    channelId = channelId,
                    userId = uid,
                    subscriberCount = subscribers.coerceAtLeast(0L),
                    viewCount = views.coerceAtLeast(0L),
                    videoCount = videos.coerceAtLeast(0L),
                    likeCount = likes.coerceAtLeast(0L),
                    recordedAtMillis = System.currentTimeMillis(),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    private suspend fun checkAndEmitMilestones(
        uid: String,
        prev: CachedChannelEntity,
        newSubs: Long,
        newVideos: Long,
        newLikes: Long,
        title: String,
    ) {
        val step = prev.milestoneStep.coerceAtLeast(100L)
        val prevBucket = prev.subscriberCount / step
        val newBucket = newSubs / step

        if (newBucket > prevBucket) {
            val crossedValue = newBucket * step
            val delta = newSubs - prev.subscriberCount
            val alertTitle = "$title crossed ${NumberFormatter.compact(crossedValue)} subscribers"
            val alertMessage = "Live subscriber count reached ${NumberFormatter.exact(newSubs)} (+${NumberFormatter.exact(delta)})."
            createAlertAndNotify(
                uid = uid,
                channelId = prev.channelId,
                channelTitle = title,
                metricType = AlertMetricType.SUBSCRIBERS,
                alertTitle = alertTitle,
                alertMessage = alertMessage,
                milestoneValue = crossedValue,
                deltaValue = delta,
            )
        } else if (newVideos > prev.videoCount) {
            val delta = newVideos - prev.videoCount
            val alertTitle = "New upload on $title"
            val alertMessage = "Public video count increased to ${NumberFormatter.exact(newVideos)} (+$delta)."
            createAlertAndNotify(
                uid = uid,
                channelId = prev.channelId,
                channelTitle = title,
                metricType = AlertMetricType.VIDEOS,
                alertTitle = alertTitle,
                alertMessage = alertMessage,
                milestoneValue = newVideos,
                deltaValue = delta,
            )
        } else if (newLikes - prev.latestLikeCount >= 500L) {
            val delta = newLikes - prev.latestLikeCount
            val alertTitle = "Like surge on $title"
            val alertMessage = "Recent videos gained +${NumberFormatter.exact(delta)} likes (total ${NumberFormatter.compact(newLikes)})."
            createAlertAndNotify(
                uid = uid,
                channelId = prev.channelId,
                channelTitle = title,
                metricType = AlertMetricType.LIKES,
                alertTitle = alertTitle,
                alertMessage = alertMessage,
                milestoneValue = newLikes,
                deltaValue = delta,
            )
        }
    }

    suspend fun createAlertAndNotify(
        uid: String,
        channelId: String,
        channelTitle: String,
        metricType: AlertMetricType,
        alertTitle: String,
        alertMessage: String,
        milestoneValue: Long,
        deltaValue: Long,
    ): Result<Unit> {
        val alertId = "alert_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val path = "users/$uid/alerts/$alertId"
        return try {
            val payload = mapOf(
                "userId" to uid,
                "channelId" to channelId.take(64),
                "channelTitle" to channelTitle.ifBlank { "Channel" }.take(120),
                "metricType" to metricType.name,
                "title" to alertTitle.ifBlank { "Channel Alert" }.take(140),
                "message" to alertMessage.ifBlank { "New channel milestone" }.take(400),
                "milestoneValue" to milestoneValue.coerceAtLeast(0L),
                "deltaValue" to deltaValue,
                "acknowledged" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            db.collection("users").document(uid)
                .collection("alerts").document(alertId)
                .set(payload)
                .await()

            notificationManager.postMilestoneNotification(
                channelId = channelId,
                title = alertTitle,
                body = alertMessage,
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateChannelPreferences(
        channelId: String,
        milestoneStep: Long,
        alertsEnabled: Boolean,
        pinnedToWidget: Boolean,
    ): Result<Unit> {
        var path = "users/unauthenticated/channels/$channelId"
        return try {
            val uid = requireUserId()
            require(isValidFirestoreId(channelId)) { "Invalid channel ID" }
            path = "users/$uid/channels/$channelId"
            val boundedStep = milestoneStep.coerceIn(100L, 10_000_000L)

            db.collection("users").document(uid)
                .collection("channels").document(channelId)
                .update(
                    mapOf(
                        "milestoneStep" to boundedStep,
                        "alertsEnabled" to alertsEnabled,
                        "pinnedToWidget" to pinnedToWidget,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()

            val existing = dao.getChannelsSync(uid).firstOrNull { it.channelId == channelId }
            if (existing != null) {
                dao.upsertChannel(
                    existing.copy(
                        milestoneStep = boundedStep,
                        alertsEnabled = alertsEnabled,
                        pinnedToWidget = pinnedToWidget,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                )
            }
            TubePulseGlanceWidget.pushWidgetUpdate(appContext)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTrackedChannel(channelId: String): Result<Unit> {
        var path = "users/unauthenticated/channels/$channelId"
        return try {
            val uid = requireUserId()
            require(isValidFirestoreId(channelId)) { "Invalid channel ID" }
            path = "users/$uid/channels/$channelId"

            db.collection("users").document(uid)
                .collection("channels").document(channelId)
                .delete()
                .await()

            dao.deleteChannel(uid, channelId)
            dao.deleteVideosForChannel(uid, channelId)
            dao.deleteSnapshotsForChannel(uid, channelId)
            TubePulseGlanceWidget.pushWidgetUpdate(appContext)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeAlert(alertId: String): Result<Unit> {
        var path = "users/unauthenticated/alerts/$alertId"
        return try {
            val uid = requireUserId()
            require(isValidFirestoreId(alertId)) { "Invalid alert ID" }
            path = "users/$uid/alerts/$alertId"

            db.collection("users").document(uid)
                .collection("alerts").document(alertId)
                .update(
                    mapOf(
                        "acknowledged" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeAllAlerts(alertIds: List<String>): Result<Unit> {
        var path = "users/unauthenticated/alerts"
        return try {
            val uid = requireUserId()
            path = "users/$uid/alerts"
            val batch = db.batch()
            alertIds.filter { isValidFirestoreId(it) }.forEach { id ->
                val ref = db.collection("users").document(uid).collection("alerts").document(id)
                batch.update(
                    ref,
                    mapOf(
                        "acknowledged" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun syncCloudChannelsToRoom(userId: String, cloud: List<TrackedChannel>) {
        val localMap = dao.getChannelsSync(userId).associateBy { it.channelId }
        val entities = cloud.map { item ->
            val prev = localMap[item.channelId]
            CachedChannelEntity(
                channelId = item.channelId,
                userId = userId,
                title = item.title,
                handle = item.handle.orEmpty(),
                description = item.description.orEmpty(),
                thumbnailUrl = item.thumbnailUrl.orEmpty(),
                subscriberCount = item.subscriberCount,
                previousSubscriberCount = prev?.subscriberCount ?: item.subscriberCount,
                viewCount = item.viewCount,
                previousViewCount = prev?.viewCount ?: item.viewCount,
                videoCount = item.videoCount,
                latestLikeCount = item.latestLikeCount,
                previousLikeCount = prev?.latestLikeCount ?: item.latestLikeCount,
                milestoneStep = item.milestoneStep,
                alertsEnabled = item.alertsEnabled,
                pinnedToWidget = item.pinnedToWidget,
                hiddenSubscriberCount = item.hiddenSubscriberCount,
                uploadsPlaylistId = item.uploadsPlaylistId.orEmpty(),
                lastSyncedAtMillis = item.lastSyncedAt?.toDate()?.time
                    ?: prev?.lastSyncedAtMillis
                    ?: System.currentTimeMillis(),
                updatedAtMillis = item.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
            )
        }
        dao.upsertChannels(entities)
    }

    private fun PublicYouTubeExtractor.PublicChannel.toSearchResult(): YouTubeChannelSearchResult =
        YouTubeChannelSearchResult(
            channelId = channelId,
            title = title,
            handle = handle,
            description = description,
            thumbnailUrl = thumbnailUrl,
            subscriberCount = subscriberCount.coerceAtLeast(0L),
            viewCount = 0L,
            videoCount = videoCount.coerceAtLeast(0L),
            hiddenSubscriberCount = hiddenSubscriberCount,
            uploadsPlaylistId = "",
        )

    private fun computeSmartMilestoneStep(subs: Long): Long = when {
        subs >= 10_000_000L -> 100_000L
        subs >= 1_000_000L -> 50_000L
        subs >= 100_000L -> 10_000L
        subs >= 10_000L -> 1_000L
        else -> 100L
    }

    private fun isValidFirestoreId(id: String): Boolean {
        return id.length in 1..128 && id.matches(Regex("^[a-zA-Z0-9_\\-]+$"))
    }
}
