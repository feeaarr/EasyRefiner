import sys
import subprocess
import os
import re
import gradio as gr
import json
import time

sys.path.append("../PaddleReviewer-server")
sys.path.append("../PaddleReviewer-web")
from models.plms.inference.CRInferenceModel import cr_model
from ModelConfig import ModelConfig
#生成评论
from models.llms import model
#生成代码细化建议
from models.llms import model2


def quickstart(diffs: str, model_config: ModelConfig):
    if model_config.method == 'llm':
        review = model.create_review(diffs,
                                     model_config.context,
                                     model_config.model_name,
                                     model_config.temperature,
                                     model_config.max_tokens,
                                     model_config.api_key,
                                     model_config.base_url)
        result = {"result": 1, "review": review}  # name = diff
    else:
        result = cr_model.predict_review(diffs)  # name = diff
    return result


def quickstart_refinement(diff_output: str, comment: str, model_config: ModelConfig):
    if model_config.method == 'llm':
        refined_code = model2.refine_code(diff_output,
                                         model_config.context,
                                         comment,
                                         model_config.model_name,
                                         model_config.temperature,
                                         model_config.max_tokens,
                                         model_config.api_key,
                                         model_config.base_url)
        result = {"result": 1, "refined_code": refined_code}  # result = 1 indicates success
    else:
        # Fallback to an alternative method if LLM is not selected
        result = {"result": 0, "refined_code": "Refinement method not supported."}  # result = 0 indicates failure
    return result

def get_commit_changes(repo_name):
    try:
        result = subprocess.run(['git', '-C', repo_name, 'show', '--stat', '-1'], check=True, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE)
        return result.stdout.decode('utf-8')
    except subprocess.CalledProcessError:
        return "Failed to get commit changes."


# num==1 means dictionary, num==2 means string
def get_diff(repo_name, num_commits, num):
    changes = []
    all_diff_output = ""  # Used to save all diff output strings

    try:
        for i in range(num_commits):
            if i == 0:
                result = subprocess.run(['git', '-C', repo_name, 'diff', 'HEAD~1', 'HEAD'], check=True,
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                commit_label = 'HEAD~1 vs HEAD'
            else:
                result = subprocess.run(['git', '-C', repo_name, 'diff', f'HEAD~{i + 1}', f'HEAD~{i}'], check=True,
                                        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                commit_label = f'HEAD~{i + 1} vs HEAD~{i}'

            # Get diff output and convert to string
            diff_output = result.stdout.decode('utf-8')

            if num == '1':
                # If need to return dictionary format
                change = parse_diff(diff_output)  # You need to define the parse_diff function to parse diff
                changes.append({
                    'commit': commit_label,
                    'changes': change
                })
            elif num == '2':
                # If need to return merged string format
                all_diff_output += f"Commit: {commit_label}\n{diff_output}\n\n"

        if num == '1':
            return changes  # Return dictionary format result
        elif num == '2':
            return all_diff_output  # Return merged string

    except subprocess.CalledProcessError:
        return "Failed to get diff information."


def get_file_content_for_commits(repo_name, num_commits):
    file_contents = []  # Used to store file content for each version

    # Get all diff information to avoid repeated git diff calls
    diff_data = get_diff(repo_name, num_commits, '1')  # Get all diff information

    try:
        for i in range(num_commits):
            commit_label = f'HEAD~{i + 1}'

            # Get current commit changes from already fetched diff data
            diff_output = diff_data[i]  # Get corresponding commit diff data
            file_changes = diff_output['changes']

            for file_change in file_changes:
                file_path = file_change['old_path']  # Use old path as reference
                try:
                    # Use git show command to get file content for specified version
                    result = subprocess.run(
                        ['git', '-C', repo_name, 'show', f'{commit_label}:{file_path}'],
                        check=True,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE
                    )
                    context = result.stdout.decode('utf-8')

                    # Filter first 100 lines containing import
                    filtered_context = filter_import_lines(context)

                    file_contents.append({
                        'commit': commit_label,
                        'file_path': file_path,
                        'context': filtered_context
                    })

                except subprocess.CalledProcessError as e:
                    print(f"Failed to get file content: {e}")
                except FileNotFoundError:
                    print(f"File {file_path} not found")
                except Exception as e:
                    print(f"Error reading file: {e}")
    except subprocess.CalledProcessError:
        print("Failed to get file content.")

    return file_contents


def filter_import_lines(context):

    lines = context.splitlines()
    import_lines = []  # 用于存储所有的 import 行

    for line in lines:
        if line.strip().startswith('import') or line.strip().startswith('from'):
            import_lines.append(line)

    # 将 import 行与其他行合并
    final_lines = import_lines + [line for line in lines if line not in import_lines]

    # 返回前 100 行
    return '\n'.join(final_lines[:100])


def parse_diff(diff_output):
    changes = []
    current_file = None
    current_diff = []
    file_found = False  # 新增变量，用于判断是否已经找到第一个文件

    for line in diff_output.splitlines():
        if line.startswith("diff --git"):
            # 如果已经处理过第一个文件，则停止解析
            if file_found:
                break

            # 解析文件路径
            match = re.search(r'diff --git a/(.+) b/(.+)', line)
            if match:
                current_file = {
                    'old_path': match.group(1),
                    'new_path': match.group(2)
                }
                file_found = True  # 找到第一个文件，设置标记

        elif current_file is not None and not line.startswith('---') and not line.startswith('+++'):
            current_diff.append(line)

    if current_file is not None:
        changes.append({
            'old_path': current_file['old_path'],
            'new_path': current_file['new_path'],
            'diff': '\n'.join(current_diff)
        })

    return changes


def generate_reviews(commit_changes, diff_output, model_config):
    results = {}
    for change in diff_output:
        commit_label = change['commit']
        results[commit_label] = []

        for file_change in change['changes']:
            old_path = file_change['old_path']
            new_path = file_change['new_path']
            diff_content = file_change['diff']

            # 生成代码审查意见
            result = quickstart(diff_content, model_config)

            if result['result'] == 1:
                # 生成代码细化建议
                refinement_result = quickstart_refinement(diff_content, result['review'], model_config)

                results[commit_label].append({
                    'old_path': old_path,
                    'new_path': new_path,
                    'result': result,
                    'refinement': refinement_result  # 添加细化建议结果
                })

    return results


def create_model_config(method, model_name, temperature, max_output_tokens, api_key, base_url, context):
    return ModelConfig(
        method,
        model_name,
        temperature,
        max_output_tokens,
        api_key,
        base_url,
        context
    )


def process_input(input_text, method, model_name, api_key, base_url, temperature, max_output_tokens, num_commits):
    progress = gr.Progress()
    # 最初定义一个空的 model_config
    model_config = create_model_config(method, model_name, temperature, max_output_tokens, api_key, base_url, context="")

    # 如果输入是代码段，直接调用 quickstart 方法
    if not (input_text.startswith("http://") or input_text.startswith("https://")):
        context = input_text
        model_config.context = context  # 将整个代码段赋值给 context
        progress(0, desc="Analyzing code...")  # 显示处理代码的进度

        # Generate code review comments
        result = quickstart(input_text, model_config)

        # Generate code refinement suggestions
        if result['result'] == 1:
            refinement_result = quickstart_refinement(input_text, result['review'], model_config)
        else:
            refinement_result = {"result": 0, "refined_code": "No refinement suggestions needed."}
        print("--------------")
        print(refinement_result)
        print("--------------")
        # Combine results
        result_str = "Suggestions for modification" if result['result'] == 1 else "Good code"
        review_result_str = f"<strong>Code review result：</strong>{result_str}<br><strong>Code review comments：</strong>{result['review']}"
        refinement_str = f"<strong>Code refinement suggestions：</strong><br>{refinement_result['refined_code']}"


        return (f"<strong>Your input code is:</strong> {input_text}<br><br>"
                f"<strong>Processing result:</strong><br>{review_result_str}<br>{refinement_str}")

    # Otherwise, if the input is a Git URL, clone the repository
    repo_name = input_text.split('/')[-1].replace('.git', '')

    if not os.path.exists(repo_name):
        progress(0, desc="Cloning code...")  # 显示克隆仓库中的进度
        try:
            subprocess.run(['git', 'clone', input_text], check=True)
            progress(100, desc="Clone complete! Analyzing code...")
        except subprocess.CalledProcessError:
            return "Failed to clone repository, please check if the URL is correct."

    commit_changes = get_commit_changes(repo_name)
    progress(0, desc="Analyzing code...")  # 显示分析代码的进度
    diff_output = get_diff(repo_name, num_commits, num='1')  # 获取字典格式的 diff 信息

    # 获取每个提交的文件内容
    file_contents = get_file_content_for_commits(repo_name, num_commits)

    # Generate review results
    results = {}
    for commit in diff_output:
        commit_label = commit['commit']
        results[commit_label] = []

        # Find the context corresponding to the current commit_label
        for content in file_contents:
            if content['commit'] == commit_label.split(' vs ')[0]:  # Match the first half of commit_label
                model_config.context = content['context']  # Update the context of model_config
                break

        for file_change in commit['changes']:
            old_path = file_change['old_path']
            new_path = file_change['new_path']
            diff_content = file_change['diff']

            # Generate code review comments
            result = quickstart(diff_content, model_config)

            if result['result'] == 1:
                # Generate code refinement suggestions
                refinement_result = quickstart_refinement(diff_content, result['review'], model_config)

                results[commit_label].append({
                    'old_path': old_path,
                    'new_path': new_path,
                    'result': result,
                    'refinement': refinement_result  # Add refinement suggestion result
                })

    # Combine results
    combined_results = []
    for commit, changes in results.items():
        if len(changes) > 0:
            # Display commit information in bold black
            combined_results.append(f"<strong>commit {commit} :</strong>")
            for r in changes:
                # Display code review results and comments in bold black
                result_str = "Suggestions for modification" if r['result']['result'] == 1 else "Good code"
                review_result_str = f"<strong>Code review result：</strong>{result_str}<br><strong>Code review comments：</strong>{r['result']['review']}"
                combined_results.append(review_result_str)

                # Display code refinement suggestions (including title and content) in bold black
                if r['refinement']['result'] == 1:
                    refinement_str = f"<strong>Code refinement suggestions：</strong><br>{r['refinement']['refined_code']}"
                    combined_results.append(refinement_str)
        else:
            # If there are no changes, display commit information in bold black
            combined_results.append(f"<strong>commit {commit}</strong>")

    # Generate difference information
    diff_info = ""
    for commit in diff_output:
        diff_info += f"<strong>commit {commit['commit']}</strong><br>"
        for change in commit['changes']:
            diff_info += f"Source path: {change['old_path']}<br>"
            diff_info += f"New path: {change['new_path']}<br>"
            diff = change['diff'].replace("\n", '<br>')
            diff_info += f"Changed code：<br>{diff}<br>"

    additional_info = f"<strong>Commit information：</strong><br>{commit_changes}<br><br>" \
                      f"<strong>Code diff：</strong><br>{diff_info}<br><br>" \
 \
        # Display the content of the processing results in black
    results_display = "\n".join(combined_results)
    results_display = results_display.replace("\n", "<br>")

    processed_results = f"<strong>Processing result：</strong><br>{results_display}"

    progress(100, desc="Analysis complete!")
    return (
        f"<strong>Project path:</strong> {os.path.abspath(repo_name)}<br>{additional_info}<br>{processed_results}"
    )


method_options = ["llm", "ft"]


demo = gr.Interface(
    fn=process_input,
    inputs=[
        gr.Textbox(label="Git URL or Code", placeholder="Please enter Git repository URL or code snippet...",
                   value="https://gitee.com/feeaarr/springooo.git", lines=2),
        gr.Dropdown(choices=method_options, label="Select Method", value="llm"),
        gr.Textbox(label="Model_Name", placeholder="Please enter Model Name", value="deepseek-chat", lines=1),
        gr.Textbox(label="API_KEY", placeholder="Please enter API_KEY", value="sk-91575fda5fbe47a8b2cb03940f728ed6",
                   lines=1),
        gr.Textbox(label="BASE_URL", placeholder="Please enter BASE_URL", value="https://api.deepseek.com", lines=1),
        gr.Slider(minimum=0, maximum=1, step=0.1, label="Select Temperature", value=0.9),
        gr.Number(label="The maximum number of output tokens.", value=500),
        gr.Slider(minimum=1, maximum=10, step=1, label="Compare the previous number of versions.", value=1)
    ],
    outputs="html",
    title="EasyRefiner",
    description="Enter a Git repository URL or a code snippet,"
                "and it will generate corresponding comments and code refinement suggestions. "
                "If a Git repository URL is entered, it will also display commit information, diff information, etc.",
    theme="Soft",
    css=f"""
        .footer {{ display: none; }}
        .gradio-container {{
            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
            border-radius: 10px;
            padding: 20px;
            height: 100vh; 
        }}
    """
)

demo.launch()