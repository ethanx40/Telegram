package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LLMTranslateConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * LLM 翻译设置页面
 *
 * 支持多种 LLM 提供商预设，用户只需：
 * 1. 选择提供商（默认 DeepSeek）
 * 2. 填写 API Key
 * 3. 可选择不同模型
 * 即可使用翻译功能。
 */
public class LLMTranslateSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;

    private int rowCount;
    private int enableRow;
    private int enableInfoRow;
    private int providerHeaderRow;
    private int providerRow;
    private int apiKeyRow;
    private int modelRow;
    private int apiEndpointRow;
    private int providerInfoRow;
    private int translateHeaderRow;
    private int autoTranslateIncomingRow;
    private int autoTranslateOutgoingRow;
    private int myLanguageRow;
    private int translateInfoRow;
    private int advancedHeaderRow;
    private int maxTokensRow;
    private int batchDelayRow;
    private int systemPromptRow;
    private int advancedInfoRow;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    private void updateRows() {
        rowCount = 0;
        enableRow = rowCount++;
        enableInfoRow = rowCount++;
        providerHeaderRow = rowCount++;
        providerRow = rowCount++;
        apiKeyRow = rowCount++;
        modelRow = rowCount++;
        apiEndpointRow = rowCount++;
        providerInfoRow = rowCount++;
        translateHeaderRow = rowCount++;
        autoTranslateIncomingRow = rowCount++;
        autoTranslateOutgoingRow = rowCount++;
        myLanguageRow = rowCount++;
        translateInfoRow = rowCount++;
        advancedHeaderRow = rowCount++;
        maxTokensRow = rowCount++;
        batchDelayRow = rowCount++;
        systemPromptRow = rowCount++;
        advancedInfoRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("LLM Translate");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        adapter = new ListAdapter(context);
        listView.setAdapter(adapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            LLMTranslateConfig config = LLMTranslateConfig.getInstance();

            if (position == enableRow) {
                if (!config.isConfigured()) {
                    // 未配置时提示先填 Key
                    AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                    b.setTitle("API Key Required");
                    b.setMessage("Please select a provider and enter your API Key first.");
                    b.setPositiveButton("OK", null);
                    b.show();
                    return;
                }
                boolean newValue = !config.isEnabled();
                config.setEnabled(newValue);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newValue);
                }
            } else if (position == providerRow) {
                showProviderSelectDialog(config);
            } else if (position == apiKeyRow) {
                showEditDialog("API Key (" + config.getCurrentProvider().displayName + ")",
                        config.getApiKey(), true, value -> {
                    config.setApiKey(value);
                    adapter.notifyItemChanged(position);
                    adapter.notifyItemChanged(enableRow);
                });
            } else if (position == modelRow) {
                showModelSelectDialog(config);
            } else if (position == apiEndpointRow) {
                showEditDialog("API Endpoint", config.getApiEndpoint(), false, value -> {
                    config.setApiEndpoint(value);
                    adapter.notifyItemChanged(position);
                });
            } else if (position == autoTranslateIncomingRow) {
                boolean newValue = !config.isAutoTranslateIncoming();
                config.setAutoTranslateIncoming(newValue);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newValue);
                }
            } else if (position == autoTranslateOutgoingRow) {
                boolean newValue = !config.isAutoTranslateOutgoing();
                config.setAutoTranslateOutgoing(newValue);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newValue);
                }
            } else if (position == myLanguageRow) {
                showLanguageSelectDialog(config);
            } else if (position == maxTokensRow) {
                showEditDialog("Max Tokens", String.valueOf(config.getMaxTokensPerRequest()), false, value -> {
                    try { config.setMaxTokensPerRequest(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                    adapter.notifyItemChanged(position);
                });
            } else if (position == batchDelayRow) {
                showEditDialog("Batch Delay (ms)", String.valueOf(config.getBatchDelayMs()), false, value -> {
                    try { config.setBatchDelayMs(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
                    adapter.notifyItemChanged(position);
                });
            } else if (position == systemPromptRow) {
                showEditDialog("System Prompt", config.getSystemPrompt(), false, value -> {
                    config.setSystemPrompt(value);
                    adapter.notifyItemChanged(position);
                });
            }
        });

        return fragmentView;
    }

    // ==================== 提供商选择弹窗 ====================

    private void showProviderSelectDialog(LLMTranslateConfig config) {
        LLMTranslateConfig.ProviderPreset[] providers = LLMTranslateConfig.PROVIDERS;
        String currentId = config.getProviderId();

        String[] names = new String[providers.length];
        for (int i = 0; i < providers.length; i++) {
            String name = providers[i].displayName;
            // 已有 Key 的打勾标记
            String savedKey = config.getApiKey();
            if (providers[i].id.equals(currentId)) {
                name = "✓ " + name;
            }
            names[i] = name;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Select Provider");
        builder.setItems(names, (dialog, which) -> {
            config.switchProvider(providers[which].id);
            // 刷新整个列表（提供商、Key、模型、端点都可能变）
            adapter.notifyDataSetChanged();
        });
        builder.show();
    }

    // ==================== 模型选择弹窗 ====================

    private void showModelSelectDialog(LLMTranslateConfig config) {
        LLMTranslateConfig.ProviderPreset provider = config.getCurrentProvider();
        String[] models = provider.availableModels;

        if (models == null || models.length == 0) {
            // Custom 提供商，手动输入
            showEditDialog("Model Name", config.getModelName(), false, value -> {
                config.setModelName(value);
                adapter.notifyItemChanged(modelRow);
            });
            return;
        }

        // 添加 "Custom..." 选项让用户手动输入
        String[] options = new String[models.length + 1];
        String currentModel = config.getModelName();
        for (int i = 0; i < models.length; i++) {
            options[i] = models[i].equals(currentModel) ? "✓ " + models[i] : models[i];
        }
        options[models.length] = "Custom...";

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Select Model");
        builder.setItems(options, (dialog, which) -> {
            if (which < models.length) {
                config.setModelName(models[which]);
                adapter.notifyItemChanged(modelRow);
            } else {
                showEditDialog("Model Name", config.getModelName(), false, value -> {
                    config.setModelName(value);
                    adapter.notifyItemChanged(modelRow);
                });
            }
        });
        builder.show();
    }

    // ==================== 通用编辑弹窗 ====================

    private void showEditDialog(String title, String currentValue, boolean isPassword,
                                Callback<String> onSave) {
        Context context = getParentActivity();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setText(currentValue);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setBackground(null);
        editText.setLineColors(
                Theme.getColor(Theme.key_dialogInputField),
                Theme.getColor(Theme.key_dialogInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        editText.setPadding(0, dp(4), 0, dp(4));
        boolean multiLine = title.equals("System Prompt");
        editText.setSingleLine(!multiLine);
        if (multiLine) {
            editText.setMaxLines(6);
        }
        if (isPassword) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        FrameLayout container = new FrameLayout(context);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);

        builder.setPositiveButton("Save", (dialog, which) -> onSave.run(editText.getText().toString().trim()));
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ==================== 语言选择弹窗 ====================

    private void showLanguageSelectDialog(LLMTranslateConfig config) {
        ArrayList<TranslateController.Language> languages = TranslateController.getLanguages();
        String[] names = new String[languages.size()];
        for (int i = 0; i < languages.size(); i++) {
            TranslateController.Language lang = languages.get(i);
            names[i] = lang.displayName + (lang.ownDisplayName != null ? " (" + lang.ownDisplayName + ")" : "");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("My Language");
        builder.setItems(names, (dialog, which) -> {
            config.setMyLanguage(languages.get(which).code);
            adapter.notifyItemChanged(myLanguageRow);
        });
        builder.show();
    }

    private interface Callback<T> {
        void run(T value);
    }

    // ==================== 列表适配器 ====================

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int pos = holder.getAdapterPosition();
            return pos != enableInfoRow && pos != providerInfoRow &&
                    pos != translateInfoRow && pos != advancedInfoRow &&
                    pos != providerHeaderRow && pos != translateHeaderRow &&
                    pos != advancedHeaderRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCheckCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextSettingsCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextInfoPrivacyCell(context);
                    break;
                case 4:
                    view = new TextDetailSettingsCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                    view = new ShadowSectionCell(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            LLMTranslateConfig config = LLMTranslateConfig.getInstance();
            LLMTranslateConfig.ProviderPreset provider = config.getCurrentProvider();

            switch (holder.getItemViewType()) {
                case 0: { // TextCheckCell
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == enableRow) {
                        cell.setTextAndCheck("Enable LLM Translation", config.isEnabled(), true);
                    } else if (position == autoTranslateIncomingRow) {
                        cell.setTextAndCheck("Auto-translate incoming", config.isAutoTranslateIncoming(), true);
                    } else if (position == autoTranslateOutgoingRow) {
                        cell.setTextAndCheck("Auto-translate outgoing", config.isAutoTranslateOutgoing(), true);
                    }
                    break;
                }
                case 1: { // TextSettingsCell
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == maxTokensRow) {
                        cell.setTextAndValue("Max Tokens", String.valueOf(config.getMaxTokensPerRequest()), true);
                    } else if (position == batchDelayRow) {
                        cell.setTextAndValue("Batch Delay", config.getBatchDelayMs() + " ms", true);
                    } else if (position == systemPromptRow) {
                        String prompt = config.getSystemPrompt();
                        if (prompt.length() > 40) prompt = prompt.substring(0, 40) + "...";
                        cell.setTextAndValue("System Prompt", prompt, false);
                    }
                    break;
                }
                case 2: { // HeaderCell
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == providerHeaderRow) {
                        cell.setText("Provider & API");
                    } else if (position == translateHeaderRow) {
                        cell.setText("Translation Settings");
                    } else if (position == advancedHeaderRow) {
                        cell.setText("Advanced");
                    }
                    break;
                }
                case 3: { // TextInfoPrivacyCell
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == enableInfoRow) {
                        cell.setText("Use LLM to translate messages. Select a provider below and enter your API Key.");
                    } else if (position == providerInfoRow) {
                        cell.setText("Select a provider, fill in the API Key, and you're ready to go. " +
                                "Endpoint and model are auto-configured. " +
                                "Choose \"Custom\" for any OpenAI-compatible API.");
                    } else if (position == translateInfoRow) {
                        cell.setText("Incoming: received messages → your language.\n" +
                                "Outgoing: your messages → recipient's language (set per chat).");
                    } else if (position == advancedInfoRow) {
                        cell.setText("Use {target_language} placeholder in system prompt.");
                    }
                    break;
                }
                case 4: { // TextDetailSettingsCell
                    TextDetailSettingsCell cell = (TextDetailSettingsCell) holder.itemView;
                    if (position == providerRow) {
                        cell.setTextAndValue("Provider", provider.displayName, true);
                    } else if (position == apiKeyRow) {
                        String key = config.getApiKey();
                        String display;
                        if (TextUtils.isEmpty(key)) {
                            display = "Not set — tap to enter";
                        } else {
                            display = "••••••••" + key.substring(Math.max(0, key.length() - 4));
                        }
                        cell.setTextAndValue("API Key", display, true);
                    } else if (position == modelRow) {
                        cell.setTextAndValue("Model", config.getModelName(), true);
                    } else if (position == apiEndpointRow) {
                        String ep = config.getApiEndpoint();
                        if (ep.length() > 50) ep = ep.substring(0, 50) + "...";
                        cell.setTextAndValue("Endpoint", ep, false);
                    } else if (position == myLanguageRow) {
                        String lang = config.getMyLanguage();
                        cell.setTextAndValue("My Language", lang != null ? lang : "Auto", false);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == enableRow || position == autoTranslateIncomingRow || position == autoTranslateOutgoingRow) {
                return 0;
            } else if (position == maxTokensRow || position == batchDelayRow || position == systemPromptRow) {
                return 1;
            } else if (position == providerHeaderRow || position == translateHeaderRow || position == advancedHeaderRow) {
                return 2;
            } else if (position == enableInfoRow || position == providerInfoRow || position == translateInfoRow || position == advancedInfoRow) {
                return 3;
            } else if (position == providerRow || position == apiKeyRow || position == modelRow ||
                    position == apiEndpointRow || position == myLanguageRow) {
                return 4;
            }
            return 5;
        }
    }
}
