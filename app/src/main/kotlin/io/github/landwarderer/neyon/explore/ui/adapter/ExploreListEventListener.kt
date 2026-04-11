package io.github.landwarderer.neyon.explore.ui.adapter

import android.view.View
import io.github.landwarderer.neyon.list.ui.adapter.ListHeaderClickListener
import io.github.landwarderer.neyon.list.ui.adapter.ListStateHolderListener

interface ExploreListEventListener : ListStateHolderListener, View.OnClickListener, ListHeaderClickListener
