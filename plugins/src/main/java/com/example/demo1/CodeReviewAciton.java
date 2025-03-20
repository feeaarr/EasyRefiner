package com.example.demo1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler;
import com.intellij.vcsUtil.VcsUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CodeReviewAciton extends AnAction {
    private String temperature ;
    private String maxToken;
    private String modelType ;

    private String model_name;

    private String api_key;
    private String base_url ;




    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject(); // 获取当前项目
        getUserConfiguration();
        AbstractCommitWorkflowHandler<?, ?> commitWorkflowHandler = (AbstractCommitWorkflowHandler<?, ?>) e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER);
        Change[] changes = commitWorkflowHandler.getUi().getIncludedChanges().toArray(new Change[0]);
        Map<String, Map<String, Object>> diffInfo;
        try {
            diffInfo = getPatchStringPerFile(project, List.of(changes));
            System.out.println(diffInfo);
        } catch (VcsException ex) {
            throw new RuntimeException(ex);
        }

        // 将 diffInfo 的内容整理成字符串，按照指定格式
        List<Map<String, String>> mods = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder(); // 用于存储所有文件的 context

        String targetFilePath = null; // 用于存储目标文件的路径

        for (Map.Entry<String, Map<String, Object>> entry : diffInfo.entrySet()) {
            String fileName = entry.getKey();
            Map<String, Object> fileInfo = entry.getValue();
            String oldPath = (String) fileInfo.get("old_path");
            String newPath = (String) fileInfo.get("new_path");
            List<String> patchStrings = (List<String>) fileInfo.get("diff");

            // 确定目标文件路径
            targetFilePath = newPath != null ? newPath : oldPath;
            System.out.println("-----------------"+targetFilePath+"-----------------");

            // 读取源文件内容并提取 context
            if (oldPath != null) {
                String context = extractContextFromFile(oldPath);
                contextBuilder.append(context).append("\n\n"); // 将 context 添加到整体 context 中
            }

            StringBuilder diffContent = new StringBuilder();
            for (String patch : patchStrings) {
                // 去掉原有标签，直接拼接行内容
                String formattedPatch = patch.replace("<ide>", "")
                        .replace("<add>", "+")
                        .replace("<del>", "-")
                        .replace("\n", "\\n"); // 将换行符替换为可转义序列
                diffContent.append(formattedPatch);
            }

            Map<String, String> mod = new HashMap<>();
            mod.put("change_type", "MODIFY");
            mod.put("old_path", oldPath);
            mod.put("new_path", newPath);
            mod.put("diff", diffContent.toString());
            mods.add(mod);
        }

        // 构造请求数据
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("model_name", model_name); // 修改为具体的模型名称
        requestData.put("temperature", Float.valueOf(temperature)); // 温度设置
        requestData.put("max_tokens", Integer.valueOf(maxToken)); // 修改为具体的maxTokens值
        requestData.put("api_key", api_key); // API密钥
        requestData.put("base_url", base_url); // 基础URL
        requestData.put("context", contextBuilder.toString()); // 设置 context
        requestData.put("mods", mods); // 添加 diffInfo
        String url = modelType.equals("llm") ? "http://127.0.0.1:8000/crWithLLMs" : "http://127.0.0.1:8000/crWithPLMS";

        System.out.println(requestData);
        // 调用HTTP接口

        try {
            String response = sendPostRequest(url, requestData);
            JSONObject jsonObject = new JSONObject(response);
            Integer result = (Integer) jsonObject.get("result");
            String result_str = (String) jsonObject.get("review");
            String refine_code = (String) jsonObject.get("refine_code");
            System.out.println("Response: " + response);
            String next_Line="";
            String reviewMessage;
            if (result == 1) {
                reviewMessage = "Needs improvement";
                next_Line="\n";
            } else {
                reviewMessage = "Good code";
                refine_code=" It's fine";
            }

            // 使用 JOptionPane 弹出包含 diff 信息的文本框
            JTextArea textArea = new JTextArea("Code review result: " +  reviewMessage + "\n----------------------------\n" +"Code review comments: "
                    + result_str + "\n----------------------------\n" + "Code refinement suggestions:" +next_Line+ refine_code );
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(500, 300));

            // 添加“Replace”按钮
            Object[] options = {"Replace", "Close"};
            int choice = JOptionPane.showOptionDialog(
                    null,
                    scrollPane,
                    "Result",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[1]
            );
            // 如果用户点击了“Replace”按钮
            if (choice == 0 && targetFilePath != null && result==1 ) {
                try (FileWriter writer = new FileWriter(targetFilePath)) {
                    writer.write(refine_code);
                    JOptionPane.showMessageDialog(null, "File replaced successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(null, "Failed to replace file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else if (choice == 0 && targetFilePath != null && result==-1) {
                JOptionPane.showMessageDialog(null, "You needn't change your code ", "Success", JOptionPane.INFORMATION_MESSAGE);
            }


        } catch (IOException | JSONException e1) {
            e1.printStackTrace();
        }


    }

    private String extractContextFromFile(String absoluteFilePath) {
        StringBuilder context = new StringBuilder();
        File file = new File(absoluteFilePath);

        // 检查文件是否存在
        if (!file.exists() || file.isDirectory()) {
            System.err.println("File not found or is a directory: " + absoluteFilePath);
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean importSectionFound = false;
            int linesAfterImport = 0;
            int totalLinesRead = 0;

            while ((line = reader.readLine()) != null) {
                totalLinesRead++;

                // 检查是否找到 import 语句
                if (!importSectionFound && (line.trim().startsWith("import ") || line.trim().startsWith("from "))) {
                    importSectionFound = true;
                }

                // 如果找到 import 语句，记录其后的 100 行
                if (importSectionFound) {
                    context.append(line).append("\n");
                    linesAfterImport++;
                    if (linesAfterImport >= 100) {
                        break; // 提取 import 及其后的 100 行
                    }
                }

                // 如果没有找到 import 语句，记录前 100 行
                if (!importSectionFound && totalLinesRead <= 100) {
                    context.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read file: " + absoluteFilePath);
            e.printStackTrace();
        }
        return context.toString();
    }


    private String sendPostRequest(String url, Map<String, Object> requestData) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost postRequest = new HttpPost(url);

            // 设置请求头
            postRequest.setHeader("Content-Type", "application/json");

            // 转换请求数据为JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(requestData);

            // 设置请求体
            StringEntity entity = new StringEntity(json);
            postRequest.setEntity(entity);

            // 发送请求并获取响应
            try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
                HttpEntity responseEntity = response.getEntity();
                return EntityUtils.toString(responseEntity);
            }
        }
    }


//获得diff和路径
public Map<String, Map<String, Object>> getPatchStringPerFile(Project project, Collection<Change> selectedChanges) throws VcsException {
    String basePath = project.getBasePath(); // 获取项目根路径
    List<TextFilePatch> patches = IdeaTextPatchBuilder.buildPatch(project, selectedChanges, Path.of(basePath), false, true).stream()
            .filter(TextFilePatch.class::isInstance)
            .map(TextFilePatch.class::cast)
            .collect(Collectors.toList());

    Map<String, Map<String, Object>> patchInfoPerFile = new HashMap<>();
    for (TextFilePatch patch : patches) {
        String oldPath = patch.getBeforeName(); // 原始旧路径（可能是相对路径）
        String newPath = patch.getAfterName();  // 原始新路径（可能是相对路径）

        // 将相对路径转换为绝对路径
        if (oldPath != null) {
            oldPath = Path.of(basePath, oldPath).toString(); // 拼接项目根路径
        }
        if (newPath != null) {
            newPath = Path.of(basePath, newPath).toString(); // 拼接项目根路径
        }

        // 如果旧路径为空（例如新增文件），使用新路径作为文件名
        String fileName = oldPath != null ? oldPath : newPath;

        if (!patchInfoPerFile.containsKey(fileName)) {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("old_path", oldPath); // 存储绝对路径
            fileInfo.put("new_path", newPath);  // 存储绝对路径
            fileInfo.put("diff", new ArrayList<>());
            patchInfoPerFile.put(fileName, fileInfo);
        }

        List<String> patchStrings = (List<String>) patchInfoPerFile.get(fileName).get("diff");
        StringBuilder patchString = new StringBuilder();
        for (PatchHunk hunk : patch.getHunks()) {
            for (PatchLine line : hunk.getLines()) {
                switch (line.getType()) {
                    case CONTEXT:
                        patchString.append("<ide> ").append(line.getText()).append("\n");
                        break;
                    case ADD:
                        patchString.append("<add> ").append(line.getText()).append("\n");
                        break;
                    case REMOVE:
                        patchString.append("<del> ").append(line.getText()).append("\n");
                        break;
                }
            }
        }
        patchStrings.add(patchString.toString());
    }

    return patchInfoPerFile; // 返回包含绝对路径的Map
}


    public void getUserConfiguration(){
        // 获取用户设置
        MyPluginSettings myPluginSettings = MyPluginSettings.getInstance();
        MyPluginSettings.State state = myPluginSettings.getState();

        if (state != null) {
            modelType = state.modelType;
            temperature = state.temperature;
            maxToken = state.maxToken;
            model_name=state.model_name;
            api_key = state.api_key;
            base_url = state.base_url;
        }
    }


}
