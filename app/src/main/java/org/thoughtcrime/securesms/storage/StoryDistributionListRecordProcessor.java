package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.StringUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class StoryDistributionListRecordProcessor extends DefaultStorageRecordProcessor<SignalStoryDistributionListRecord> {

  private static final String TAG = Log.tag(StoryDistributionListRecordProcessor.class);

  private boolean haveSeenMyStory;

  /**
   * At a minimum, we require:
   *  <ul>
   *    <li>A valid identifier</li>
   *    <li>A non-visually-empty name field OR a deleted at timestamp</li>
   *  </ul>
   */
  @Override
  boolean isInvalid(@NonNull SignalStoryDistributionListRecord remote) {
    UUID remoteUuid = UuidUtil.parseOrNull(remote.getIdentifier());
    if (remoteUuid == null) {
      Log.d(TAG, "Bad distribution list identifier -- marking as invalid");
      return true;
    }

    boolean isMyStory = remoteUuid.equals(DistributionId.MY_STORY.asUuid());
    if (haveSeenMyStory && isMyStory) {
      Log.w(TAG, "Found an additional MyStory record -- marking as invalid");
      return true;
    }

    haveSeenMyStory |= isMyStory;

    if (remote.getDeletedAtTimestamp() > 0L) {
      if (isMyStory) {
        Log.w(TAG, "Refusing to delete My Story -- marking as invalid");
        return true;
      } else {
        return false;
      }
    }

    if (StringUtil.isVisuallyEmpty(remote.getName())) {
      Log.d(TAG, "Bad distribution list name (visually empty) -- marking as invalid");
      return true;
    }

    return false;
  }

  @Override
  @NonNull Optional<SignalStoryDistributionListRecord> getMatching(@NonNull SignalStoryDistributionListRecord remote, @NonNull StorageKeyGenerator keyGenerator) {
    RecipientId matching = SignalDatabase.distributionLists().getRecipientIdForSyncRecord(remote);
    if (matching != null) {
      RecipientRecord recordForSync = SignalDatabase.recipients().getRecordForSync(matching);
      if (recordForSync == null) {
        throw new IllegalStateException("Found matching recipient but couldn't generate record for sync.");
      }

      return StorageSyncModels.localToRemoteRecord(recordForSync).getStoryDistributionList();
    } else {
      return Optional.empty();
    }
  }

  @Override
  @NonNull SignalStoryDistributionListRecord merge(@NonNull SignalStoryDistributionListRecord remote, @NonNull SignalStoryDistributionListRecord local, @NonNull StorageKeyGenerator keyGenerator) {
    byte[]                     unknownFields      = remote.serializeUnknownFields();
    byte[]                     identifier         = remote.getIdentifier();
    String                     name               = remote.getName();
    List<SignalServiceAddress> recipients         = remote.getRecipients();
    long                       deletedAtTimestamp = remote.getDeletedAtTimestamp();
    boolean                    allowsReplies      = remote.allowsReplies();
    boolean                    isBlockList        = remote.isBlockList();

    boolean matchesRemote = doParamsMatch(remote, unknownFields, identifier, name, recipients, deletedAtTimestamp, allowsReplies, isBlockList);
    boolean matchesLocal  = doParamsMatch(local, unknownFields, identifier, name, recipients, deletedAtTimestamp, allowsReplies, isBlockList);

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalStoryDistributionListRecord.Builder(keyGenerator.generate(), unknownFields)
                                                  .setIdentifier(identifier)
                                                  .setName(name)
                                                  .setRecipients(recipients)
                                                  .setDeletedAtTimestamp(deletedAtTimestamp)
                                                  .setAllowsReplies(allowsReplies)
                                                  .setIsBlockList(isBlockList)
                                                  .build();
    }
  }

  @Override
  void insertLocal(@NonNull SignalStoryDistributionListRecord record) throws IOException {
    SignalDatabase.distributionLists().applyStorageSyncStoryDistributionListInsert(record);
  }

  @Override
  void updateLocal(@NonNull StorageRecordUpdate<SignalStoryDistributionListRecord> update) {
    SignalDatabase.distributionLists().applyStorageSyncStoryDistributionListUpdate(update);
  }

  @Override
  public int compare(SignalStoryDistributionListRecord o1, SignalStoryDistributionListRecord o2) {
    if (Arrays.equals(o1.getIdentifier(), o2.getIdentifier())) {
      return 0;
    } else {
      return 1;
    }
  }

  private boolean doParamsMatch(@NonNull SignalStoryDistributionListRecord record,
                                @Nullable byte[] unknownFields,
                                @Nullable byte[] identifier,
                                @Nullable String name,
                                @NonNull List<SignalServiceAddress> recipients,
                                long deletedAtTimestamp,
                                boolean allowsReplies,
                                boolean isBlockList) {
    return Arrays.equals(unknownFields, record.serializeUnknownFields()) &&
           Arrays.equals(identifier, record.getIdentifier()) &&
           Objects.equals(name, record.getName()) &&
           Objects.equals(recipients, record.getRecipients()) &&
           deletedAtTimestamp == record.getDeletedAtTimestamp() &&
           allowsReplies == record.allowsReplies() &&
           isBlockList == record.isBlockList();
  }
}
