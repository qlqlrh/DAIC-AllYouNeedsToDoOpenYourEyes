from flask import Flask, request, jsonify

app = Flask(__name__)

# 가상의 텍스트 후처리 함수
def refine_text(text):
    # 예: 마지막 마침표가 없으면 붙여주기
    if not text.endswith(".") and not text.endswith("다.") and not text.endswith("요."):
        text += "."
    return text

@app.route("/refine-text", methods=["POST"])
def refine_text_route():
    data = request.get_json()
    if not data or "text" not in data:
        return jsonify({"error": "Missing 'text' field"}), 400

    raw_text = data["text"]
    refined = refine_text(raw_text)
    return jsonify({"refinedText": refined})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5002)

