package org.thoughtcrime.securesms.stories.viewer.views

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.util.livedata.Store

class StoryViewsViewModel(private val storyId: Long, private val repository: StoryViewsRepository) : ViewModel() {

  private val store = Store(StoryViewsState(StoryViewsState.LoadState.INIT))
  private val disposables = CompositeDisposable()

  val state: LiveData<StoryViewsState> = store.stateLiveData

  fun refresh() {
    if (repository.isReadReceiptsEnabled()) {
      disposables += repository.getViews(storyId).subscribe { data ->
        store.update {
          it.copy(
            views = data,
            loadState = StoryViewsState.LoadState.READY
          )
        }
      }
    } else {
      store.update {
        it.copy(
          loadState = StoryViewsState.LoadState.DISABLED
        )
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(
    private val storyId: Long,
    private val repository: StoryViewsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryViewsViewModel(storyId, repository)) as T
    }
  }
}
