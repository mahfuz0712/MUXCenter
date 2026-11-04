package com.mux.center;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

/**
 * Compose needs a lifecycle, saved state and view model owner on the view tree.
 * An accessibility service has none, so this class provides them.
 * It is written in Java so it compiles with every lifecycle / savedstate version.
 */
public class OverlayOwner implements LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private final LifecycleRegistry registry = new LifecycleRegistry(this);
    private final SavedStateRegistryController controller = SavedStateRegistryController.create(this);
    private final ViewModelStore store = new ViewModelStore();

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return registry;
    }

    @NonNull
    @Override
    public SavedStateRegistry getSavedStateRegistry() {
        return controller.getSavedStateRegistry();
    }

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return store;
    }

    public void start() {
        controller.performRestore(null);
        registry.setCurrentState(Lifecycle.State.RESUMED);
    }

    public void stop() {
        registry.setCurrentState(Lifecycle.State.DESTROYED);
        store.clear();
    }

    public static void attach(View v, OverlayOwner o) {
        ViewTreeLifecycleOwner.set(v, o);
        ViewTreeSavedStateRegistryOwner.set(v, o);
        ViewTreeViewModelStoreOwner.set(v, o);
    }
}
