# Kawang Admin Mobile

安卓审核 App，面向荣耀 / 华为手机，提供：

- 管理员登录
- 待审核充值列表与详情
- 通过 / 驳回充值
- 在线客服会话列表与单会话聊天
- Huawei Push Kit 离线推送入口

## 本地构建前准备

1. 安装 Android Studio 与 Android SDK。
2. 在 `gradle.properties` 或本机 `~/.gradle/gradle.properties` 中配置：

```properties
HUAWEI_PUSH_APP_ID=你的华为推送 AppId
```

3. 后端需要同时配置这些环境变量：

```env
SHOP_PUSH_HUAWEI_ENABLED=true
SHOP_PUSH_HUAWEI_APP_ID=你的华为推送 AppId
SHOP_PUSH_HUAWEI_CLIENT_ID=你的华为 OAuth Client ID
SHOP_PUSH_HUAWEI_CLIENT_SECRET=你的华为 OAuth Client Secret
```

## 说明

- App 目前默认允许明文 HTTP，方便直接连测试环境；正式上线建议优先填写 HTTPS 域名。
- 推送点击会尝试通过 `route` 打开：
  - `recharges/{id}`
  - `support/{sessionId}`
- 充值审核权限仍由后端控制，只有超级管理员能执行审核动作。
