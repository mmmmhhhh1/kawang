# Docker 部署

## 1. 先在本机构建产物

### 前台

```powershell
cd D:\kawang\front
npm.cmd run build
```

### 管理端

```powershell
cd D:\kawang\admin
npm.cmd run build
```

### 后端

```powershell
cd D:\kawang\backend\kah
cmd /c .\mvnw.cmd -q -DskipTests package
```

## 2. 上传到服务器

建议目录：

```bash
/home/mhh/kawang-deploy
```

必须上传这些内容：

- `front/dist`
- `front/Dockerfile`
- `front/nginx.conf`
- `admin/dist`
- `admin/Dockerfile`
- `admin/nginx.conf`
- `backend/kah/target/kah-0.0.1-SNAPSHOT.jar`
- `backend/kah/Dockerfile`
- `docker-compose.yml`
- `.env.example`

## 3. 创建环境变量文件

在项目根目录执行：

```bash
cp .env.example .env
```

然后编辑 `.env`，至少改这些值：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `JWT_SECRET`
- `AES_KEY`
- `ADMIN_PASSWORD`

邮箱如果暂时不用，可以先保留占位值。

## 4. 启动

```bash
docker compose up -d --build
```

## 5. 查看状态

```bash
docker compose ps
docker compose logs -f backend
```

## 6. 访问地址

- 用户前台：`http://服务器IP/`
- 管理端：`http://服务器IP:8081/`

## 7. 停止

```bash
docker compose down
```

如果要连带删除数据卷：

```bash
docker compose down -v
```
