package com.example.demo1;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "MyPluginSettings",
        storages = {@Storage("cmgPlungingSettings.xml")}
)

@Service(Service.Level.APP)
public final class MyPluginSettings implements PersistentStateComponent<MyPluginSettings.State> {

    private State myState = new State();

    @Override
    public @Nullable MyPluginSettings.State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.myState = state;
    }

    static class State {
        public String modelType = "llm";
        public String model_name = "deepseek-chat";
        public String api_key = "sk-91575fda5fbe47a8b2cb03940f728ed6";
        public String base_url = "https://api.deepseek.com/";

        public String temperature = "0.9";

        public String maxToken = "500";

    }

    public static MyPluginSettings getInstance() {
        return ApplicationManager.getApplication().getService(MyPluginSettings.class);
    }
}
