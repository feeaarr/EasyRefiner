from openai import OpenAI

def refine_code(diff_output, context, comment, model_name, temperature, max_tokens, api_key, base_url):
    """
    根据差异信息和审查意见生成完整的代码块
    """
    prompt = generate_refinement_prompt(diff_output, context, comment)
    response = get_openai_response(prompt, model_name, temperature, max_tokens, api_key, base_url)
    return extract_full_code(response)

def generate_refinement_prompt(diff_output, context, comment):

    prompt = (
        "You are a code refinement assistant. Given the following code diff, context and review comment:\n\n"
        "**Code Diff (for reference):**\n"
        f"```diff\n{diff_output}\n```\n\n"
        "**Full File Context:**\n"
        f"```java\n{context}\n```\n\n"
        "**Review Comment:**\n"
        f"{comment}\n\n"
        "Please generate the revised code according to the review."
        "Please ensure that the revised code follows the original code format and comments, "
        "unless it is explicitly required by the review."
    )
    return prompt

def get_openai_response(prompt, model_name, temperature, max_tokens, api_key, base_url):
    client = OpenAI(api_key=api_key, base_url=base_url)
    response = client.chat.completions.create(
        model=model_name,
        messages=[
            {"role": "system", "content": "You are a Java code refinement expert."},
            {"role": "user", "content": prompt}
        ],
        temperature=temperature,
        max_tokens=max_tokens,
    )
    return response.choices[0].message.content

def extract_full_code(response):
    """
    提取完整的代码块（包含上下文）
    """
    code_block = []
    in_code = False
    for line in response.split('\n'):
        if line.strip().startswith('```java'):
            in_code = True
            continue
        if line.strip().startswith('```'):
            in_code = False
            continue
        if in_code:
            code_block.append(line)
    return '\n'.join(code_block).strip()


