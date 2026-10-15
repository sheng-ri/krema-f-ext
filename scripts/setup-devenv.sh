#!/bin/bash
# 每次新开环境都执行

npm install -g pnpm@12

cd /workspace/krema-demo/
pnpm approve-builds --all

git clone --depth 1 https://cnb.cool/BirthCat/mirror/ponytail.git /opt/ponytail \
    && codebuddy plugin marketplace add /opt/ponytail \
    && codebuddy plugin install ponytail@ponytail \
    && codebuddy plugin list \

npx --yes skills add "https://cnb.cool/BirthCat/mirror/mattpocock-skills.git" \
  -a codebuddy \
  -s code-review \
  -s domain-modeling \
  -s grill-me \
  -s improve-codebase-architecture \
  -s grill-with-docs \
  -s grilling \
  -s handoff \
  -s implement \
  -s implement-spec \
  -s pr \
  -s prototype \
  -s research \
  -s setup-matt-pocock-skills \
  -s tdd \
  -s to-spec \
  -s to-tickets \
  -s wait-what \
  -s wayfinder \
  -s writing-for-agents \
  -g \
  -y

npx --yes skills add "https://cnb.cool/BirthCat/mirror/humanlayer-skills.git" \
  -a codebuddy \
  -s show-me \
  -s visual-pr \
  -g \
  -y

npx --yes skills add "https://cnb.cool/c.d/caveman.git" \
  -a codebuddy \
  -s caveman \
  -g \
  -y

curl -Ls https://sh.jbang.dev | bash -s - app setup
mkdir ~/.config/mcp

cat > ~/.codebuddy/.mcp.json <<'EOF'
{
  "mcpServers": {
    "gradle": {
      "type": "stdio",
      "command": "/root/.jbang/bin/jbang",
      "args": [
        "run",
        "--fresh",
        "--repos",
        "tencent=https://mirrors.cloud.tencent.com/nexus/repository/maven-public/",
        "dev.rnett.gradle-mcp:gradle-mcp:0.0.16"
      ],
    }
  }
}
EOF