#!/usr/bin/env python3
"""
初始化测试数据脚本 —— 注册用户、用 assets/test 下的图片发布笔记。

用法:
    python scripts/init-test-data.py

前置条件:
    所有 Java 服务已启动（gateway :8080, note-service :8083, upload-service :8081）
    MinIO 已启动且 bucket 已创建（uploads, notes）
"""

import json
import os
import random
import sys
import time
import requests

BASE_URL = os.environ.get("GATEWAY_URL", "http://localhost:8080")
ASSETS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "assets", "test")

# ─── 预定义用户 ───────────────────────────────────────────
USERS = [
    {"username": "lily_wang",   "password": "test123456", "nickname": "莉莉Wang"},
    {"username": "coder_zhang", "password": "test123456", "nickname": "程序员小张"},
    {"username": "travel_xiao", "password": "test123456", "nickname": "旅行者小肖"},
    {"username": "chef_liu",    "password": "test123456", "nickname": "大厨刘哥"},
    {"username": "photo_ming",  "password": "test123456", "nickname": "摄影师阿明"},
    {"username": "reader_chen", "password": "test123456", "nickname": "读书人陈陈"},
]

# ─── 笔记内容库（按分类）───────────────────────────────────

NOTES = [
    # ── 旅行类 ──
    {
        "category": "travel",
        "title": "云南丽江古城三日游攻略",
        "content": (
            "丽江古城真的是一个让人流连忘返的地方。第一天我们从大水车出发，沿着石板路一路走到四方街，"
            "街边的小店琳琅满目，卖着手工银饰、东巴纸和鲜花饼。傍晚在狮子山看了日落，整个古城被染成金色，"
            "美得让人说不出话。\n\n"
            "第二天去了玉龙雪山，4680米的海拔让我有点高反，但看到那片蓝得不像话的天空和皑皑白雪，"
            "一切都值了。下午去了蓝月谷，水的颜色像翡翠一样，拍照根本不需要滤镜。\n\n"
            "第三天在束河古镇找了个咖啡馆待了一下午，跟老板聊了聊纳西族的文化和东巴文字，"
            "学到了很多。三天下来总共花费不到2000块，性价比超高。"
        ),
    },
    {
        "category": "travel",
        "title": "西藏自驾游——一个人的朝圣之旅",
        "content": (
            "今年终于实现了去西藏的梦想。从成都出发，沿318国道一路向西，途经康定、理塘、稻城，"
            "每一帧都是壁纸级别的风景。在理塘看到了此生最壮丽的银河，在稻城亚丁徒步了8小时，"
            "累到腿软但心里无比充实。\n\n"
            "最难忘的是在然乌湖边露营的那晚，半夜醒来看到月亮从雪山背后升起，湖面结了一层薄冰，"
            "整个世界安静得只听得见自己的心跳。拉萨的布达拉宫比想象中还要宏伟，在大昭寺门口看到"
            "磕长头的信徒，那种纯粹的信仰让人肃然起敬。"
        ),
    },
    {
        "category": "travel",
        "title": "日本京都红叶季——秋天最美的打开方式",
        "content": (
            "11月底去的京都，正好赶上红叶最盛的时节。岚山的竹林小径在红叶的映衬下格外迷人，"
            "天龙寺的枯山水庭院配上满园红叶，简直是禅意的极致。\n\n"
            "永观堂的夜枫一定要看——灯光打在红叶上，倒映在池水中，像进入了另一个世界。"
            "清水寺的舞台在红叶中若隐若现，拍到一张清晨无人的照片非常难得。"
        ),
    },
    {
        "category": "travel",
        "title": "大理洱海环湖骑行——200公里的治愈之旅",
        "content": (
            "在大理租了一辆电动车，沿着洱海骑了整整两天。从古城出发，顺时针环湖，"
            "一路经过喜洲古镇、双廊、挖色、小普陀。海东的公路贴着湖边，左手苍山右手洱海，"
            "风吹在脸上带着淡淡的水草香。\n\n"
            "双廊的海景民宿值得一住，推开窗就是洱海日出。喜洲古镇的粑粑一定要尝，"
            "刚出炉的又香又软。整个环湖200公里，走走停停，看到好看的风景就停下来拍照，"
            "这才是度假该有的节奏。"
        ),
    },
    {
        "category": "travel",
        "title": "新疆喀纳斯——上帝打翻的调色盘",
        "content": (
            "秋天的喀纳斯，真的不需要任何修图。白桦林一片金黄，喀纳斯湖是深邃的蓝绿色，"
            "远处的雪山作为背景，整个画面美得不真实。住在禾木村的小木屋里，"
            "清晨起来看到晨雾从河面升起，阳光穿过白桦林的缝隙，那一刻觉得人生值得。"
        ),
    },

    # ── 美食类 ──
    {
        "category": "food",
        "title": "家庭版红烧肉——软糯不腻的秘诀",
        "content": (
            "做了20年的红烧肉，总结出几个关键点：第一，五花肉一定要先焯水去腥，冷水下锅加姜片料酒；"
            "第二，炒糖色是灵魂——冰糖小火慢炒，炒到枣红色冒小泡立刻下肉；"
            "第三，一定要加热水，冷水会让肉质紧缩变柴。\n\n"
            "配料：五花肉500g、冰糖30g、生抽2勺、老抽1勺、八角2个、桂皮1块、香叶2片、葱姜适量。"
            "小火慢炖40分钟，大火收汁5分钟，出锅撒葱花。"
        ),
    },
    {
        "category": "food",
        "title": "正宗麻婆豆腐——麻辣鲜香嫩",
        "content": (
            "在成都学艺三年，师傅教的麻婆豆腐配方终于可以分享了。关键在三个字：麻、辣、烫。"
            "花椒一定要用汉源的，现磨的花椒粉香气完全不一样；郫县豆瓣酱剁细了炒出红油；"
            "牛肉末要炒到酥脆；豆腐一定要用嫩豆腐，切块后在盐水里泡10分钟，煮的时候不容易碎。"
        ),
    },
    {
        "category": "food",
        "title": "周末Brunch：牛油果班尼迪克蛋",
        "content": (
            "这道看似高级的brunch其实在家也能做。荷兰酱的关键是隔水加热时不停地搅拌，"
            "黄油要分次加入，温度控制在60度左右，太高会水油分离。水波蛋的秘诀是水里加一点白醋，"
            "水开后搅出漩涡再打蛋，3分钟刚刚好流心。配上烤脆的英式麦芬和熟透的牛油果，"
            "切开的那一刻蛋液流出来，太治愈了。"
        ),
    },
    {
        "category": "food",
        "title": "深夜食堂——日式豚骨拉面完全指南",
        "content": (
            "花了整整两天熬的豚骨汤，骨头焯水后要砸开露出骨髓，大火滚煮12小时以上，"
            "汤色才会变成奶白色。叉烧用五花肉卷起来绑紧，酱油味醂腌一晚再低温慢烤4小时。"
            "溏心蛋是6分钟刚好——蛋白凝固蛋黄流心。蒜油是点睛之笔，猪油煸蒜末到金黄，淋在面上香到邻居来敲门。"
        ),
    },
    {
        "category": "food",
        "title": "手冲咖啡入门——从选豆到冲泡",
        "content": (
            "入坑手冲咖啡半年，分享一些新手经验。豆子选埃塞俄比亚耶加雪菲入门最合适，"
            "花果香明显，容错率高。研磨度比白砂糖略粗，水温92度，粉水比1:15，"
            "闷蒸30秒后分三次注水，总萃取时间控制在2分半左右。\n\n"
            "器材方面，新手用V60滤杯+HARIO手冲壶就够了，温度计和电子秤是必需品。"
            "不要一开始就追求贵价设备，先把基本功练好更重要。"
        ),
    },

    # ── 摄影类 ──
    {
        "category": "photography",
        "title": "手机拍出单反感——5个必学构图技巧",
        "content": (
            "没有专业器材也能拍出好照片，关键是构图。分享5个实用技巧：\n\n"
            "1. 三分法——把画面横竖各分三份，主体放在交点上\n"
            "2. 引导线——用道路、栏杆、河流引导视线到主体\n"
            "3. 框架构图——利用门框、窗户、树枝做天然画框\n"
            "4. 留白——让画面有呼吸感，适合极简风格\n"
            "5. 前景——加入近处的元素增加画面层次\n\n"
            "后期用Snapseed或Lightroom Mobile简单调一下曝光和色调就够了，不要过度滤镜。"
        ),
    },
    {
        "category": "photography",
        "title": "城市夜景拍摄参数设置全攻略",
        "content": (
            "拍夜景最常见的错误就是用自动模式，结果又糊又噪。正确做法：M档，ISO调最低(100-400)，"
            "光圈f/8-f/11（星芒效果），快门根据测光表来调（通常2-30秒），三角架是必须的！\n\n"
            "蓝调时刻是拍夜景的黄金时间——日落后15-30分钟，天空是深蓝色的，"
            "城市的灯光刚刚亮起，画面冷暖对比最强烈。用快门线或2秒自拍延时避免手抖。"
        ),
    },
    {
        "category": "photography",
        "title": "人像摄影的光线运用——自然光篇",
        "content": (
            "人像摄影中光线比器材重要100倍。黄金时段（日出后一小时和日落前一小时）的光线柔和温暖，"
            "是拍人像的最佳时间。逆光拍摄时用反光板补光，可以拍出发丝发光的梦幻效果。\n\n"
            "阴天其实是天然柔光箱，光线均匀没有硬阴影，特别适合拍小清新风格。"
            "正午大太阳下怎么拍？找树荫、建筑物阴影或者用柔光板遮挡，避免脸上出现难看的光斑。"
        ),
    },
    {
        "category": "photography",
        "title": "Lightroom调色思路分享——电影感色调",
        "content": (
            "最近研究电影感调色，核心是三点：降饱和、加对比、统一色调。具体操作：\n"
            "1. 曲线拉一个S形，暗部稍微提一点保留细节\n"
            "2. HSL里把绿色往青色调偏，蓝色往青紫色调偏\n"
            "3. 分离色调：高光加暖黄色，阴影加青蓝色\n"
            "4. 加一点颗粒感模仿胶片质感，数值15-25左右\n"
            "5. 暗角效果增强氛围，数值-15到-20"
        ),
    },
    {
        "category": "photography",
        "title": "旅行vlog设备推荐——我的轻量化装备",
        "content": (
            "拍旅行vlog三年，从大包小包进化到现在一机一镜走天下。现在的装备：\n"
            "Sony A7C II（全画幅里最小最轻）+ 24-70mm f/2.8 GM II（一镜到底），"
            "DJI Pocket 3拍行走镜头稳如鸡头，收音用DJI Mic 2一拖二，"
            "无人机是Mini 4 Pro（249g不用考证）。所有装备加起来不到4kg，一个双肩包搞定。"
        ),
    },

    # ── 科技类 ──
    {
        "category": "tech",
        "title": "2024年程序员应该学什么技术栈？",
        "content": (
            "作为一个工作五年的后端开发，分享一下我对技术趋势的观察。首先Rust值得投入时间学习，"
            "Linux内核、Android、Windows都在用Rust重写关键模块，安全性和性能都是顶级的。\n\n"
            "AI方向，不一定要做算法研究员，但至少要会用LLM的API、会写Prompt、会用LangChain/RAG。"
            "这些会成为未来三年程序员的标配技能，就像现在会写SQL一样基本。\n\n"
            "云原生还是以Kubernetes为核心，但Serverless和边缘计算正在崛起。"
            "数据库方面，PostgreSQL的生态越来越强，值得从MySQL切过去。"
        ),
    },
    {
        "category": "tech",
        "title": "我的家庭服务器搭建实录——NAS+软路由+Docker",
        "content": (
            "用一台退役的HP迷你主机搭建了家庭All-in-One服务器。装了Proxmox VE做虚拟化，"
            "上面跑OpenWrt软路由（科学上网+广告过滤）、TrueNAS Scale做存储（4TB×2 RAID1）、"
            "Ubuntu Server跑Docker。\n\n"
            "Docker上部署的服务：Jellyfin（媒体中心）、HomeAssistant（智能家居）、"
            "Bitwarden（密码管理）、Nextcloud（私人云盘）、Nginx Proxy Manager（反向代理）。"
            "整机待机功耗25W，一个月电费不到10块钱。"
        ),
    },
    {
        "category": "tech",
        "title": "Git进阶——你可能不知道的10个实用命令",
        "content": (
            "git bisect —— 二分法定位bug引入的commit，比手动逐个checkout快N倍\n"
            "git reflog —— 你的后悔药，误操作后可以从这里找回丢失的commit\n"
            "git cherry-pick —— 只把一个commit应用到另一个分支，而不是合并整个分支\n"
            "git stash push -m 'msg' —— 给stash加描述，避免一堆 unnamed stash\n"
            "git log -S 'keyword' —— 搜索包含某个关键词的commit历史，俗称 pickaxe\n"
            "git blame -L 100,120 file —— 只看指定行的修改历史\n"
            "git diff --word-diff —— 按单词粒度显示diff，比按行更精确"
        ),
    },
    {
        "category": "tech",
        "title": "微服务架构踩坑总结——从单体到拆分的经验",
        "content": (
            "公司项目从单体Spring Boot拆成微服务已经跑了一年多，分享一些真实踩坑记录：\n\n"
            "1. 分布式事务是最大的坑——不要用分布式事务，用最终一致性+Saga模式\n"
            "2. 服务拆分不要太细——5-8个服务足矣，再多运维成本爆炸\n"
            "3. 日志集中收集比监控更重要——ELK或Loki是刚需，出问题翻几十个服务的日志会疯\n"
            "4. 配置中心一定要上——Nacos/Apollo二选一，改配置不用重启服务的感觉太爽了\n"
            "5. 链路追踪早点上——SkyWalking或Jaeger，排查性能瓶颈的神器"
        ),
    },
    {
        "category": "tech",
        "title": "开源AI模型本地部署实战——Ollama+Open WebUI",
        "content": (
            "在本地跑大模型比想象中简单。Ollama一键安装，然后 ollama pull llama3 下载模型，"
            "ollama run llama3 就能在终端跟AI对话了。搭配Open WebUI可以获得接近ChatGPT的网页体验。\n\n"
            "配置：RTX 4070Ti 12GB + 32GB RAM。Llama 3 8B跑起来毫无压力，"
            "Qwen 2 7B中文效果更好。如果需要写代码，deepseek-coder-v2效果很不错。"
        ),
    },

    # ── 生活/读书类 ──
    {
        "category": "lifestyle",
        "title": "断舍离一年后——我的极简生活实验报告",
        "content": (
            "去年下定决心做了一次彻底的断舍离，扔掉了两年没穿的衣服、没拆封的厨房小家电、"
            "大学时代的课本笔记。清理完之后，房间空了，心也轻了。\n\n"
            "最大的改变不是物品少了，而是购物习惯变了。买之前会问自己三个问题：真的需要吗？"
            "有替代品吗？一周后还想买吗？大部分冲动消费在第三个问题就被过滤了。"
            "一年下来省了不少钱，关键是生活品质反而提高了。"
        ),
    },
    {
        "category": "lifestyle",
        "title": "30天冥想挑战——从焦虑到平静",
        "content": (
            "作为一个曾经的严重焦虑症患者，分享我的冥想入门经历。用的是Headspace APP，"
            "从每天5分钟开始，前两周几乎坐不住，脑子里全是乱七八糟的想法。\n\n"
            "第15天开始有了变化——我发现自己的想法在冒出来之前可以先\"看到\"它，"
            "然后选择不被它带走。这个觉察的能力在工作中也帮助很大，"
            "被领导怼的时候不会立刻炸毛，能先深吸一口气再回应。\n\n"
            "推荐给每一个觉得自己停不下来的朋友。不需要坐成莲花座，椅子上闭上眼睛就行。"
        ),
    },
    {
        "category": "lifestyle",
        "title": "我的晨间 Routine——5点起床的100天",
        "content": (
            "挑战了100天5点起床，分享一下真实体验。前两周是最痛苦的，闹钟响了恨不得砸手机。"
            "但从第三周开始身体适应了，晚上9点半自然就困了。\n\n"
            "早上的两小时是全天最高效的时间——没有人打扰，手机也不响。用这段时间读书、"
            "写作或者锻炼，比晚上熬夜做同样的事效率高三倍。洗冷水澡和喝一大杯水是清醒的关键。"
        ),
    },
    {
        "category": "lifestyle",
        "title": "《原子习惯》读书笔记——微小改变的巨大力量",
        "content": (
            "这本书彻底改变了我对习惯的认知。核心观点：不要追求目标，要设计系统。"
            "每天进步1%，一年后你会变强37倍。\n\n"
            "四个法则：1. 让它显而易见（环境设计比意志力管用）2. 让它有吸引力（绑定喜欢的事）"
            "3. 让它轻而易举（两分钟原则，从最小的版本开始）4. 让它令人满足（即时奖励）。\n\n"
            "亲测最有效的是\"习惯叠加\"：把新习惯挂在已有习惯后面。比如我现在的链条是："
            "喝咖啡 → 冥想5分钟 → 写晨间日记 → 锻炼。一个触发一个，自动运行。"
        ),
    },
    {
        "category": "lifestyle",
        "title": "Room Tour——10平米出租屋的改造日记",
        "content": (
            "租房也能住出幸福感。10平米的单间，通过合理布局和收纳，打造出了工作区、"
            "休息区和读书角三个功能区。关键家具：Loft床（上面睡觉下面书桌）、"
            "洞洞板（墙面收纳神器）、可折叠餐桌（平时收起来不占空间）。\n\n"
            "灯光是氛围感的灵魂——主灯只用来找东西，台灯、落地灯、串灯才是日常使用的。"
            "色温选3000K暖黄光，会让小空间瞬间变温馨。绿植选虎皮兰和龟背竹，好看又好养。"
        ),
    },
]

# ─── 工具函数 ──────────────────────────────────────────

def register_user(user):
    """注册用户，忽略 409（已存在）"""
    r = requests.post(f"{BASE_URL}/api/auth/register", json=user, timeout=10)
    if r.status_code == 200:
        print(f"  注册成功: {user['username']}")
    elif r.status_code == 409:
        print(f"  用户已存在: {user['username']}")
    else:
        print(f"  注册失败 {user['username']}: {r.status_code} {r.text[:100]}")
    return r.ok or r.status_code == 409


def login(user):
    """登录获取 accessToken"""
    r = requests.post(f"{BASE_URL}/api/auth/login", json={
        "username": user["username"],
        "password": user["password"],
        "type": "password",
    }, timeout=10)
    if r.status_code == 200:
        data = r.json()["data"]
        return data["accessToken"]
    else:
        print(f"  登录失败 {user['username']}: {r.status_code} {r.text[:100]}")
        return None


def create_draft(token, title, content):
    """创建笔记草稿，返回 noteId"""
    r = requests.post(f"{BASE_URL}/api/note/draft",
        headers={"Authorization": f"Bearer {token}"},
        json={"title": title, "content": content},
        timeout=10)
    if r.status_code == 200:
        return r.json()["data"]["noteId"]
    else:
        print(f"  创建草稿失败: {r.status_code} {r.text[:100]}")
        return None


def get_image_info(image_path):
    """获取图片的 fileName 和 contentType"""
    ext = os.path.splitext(image_path)[1].lower()
    content_type_map = {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp",
    }
    content_type = content_type_map.get(ext, "image/jpeg")
    file_name = os.path.basename(image_path)
    return file_name, content_type


def publish_and_upload(token, note_id, image_path):
    """发布笔记并上传封面图到 note-service 返回的 presigned URL"""
    file_name, content_type = get_image_info(image_path)

    # 发布笔记，获取 note-service 生成的 presigned PUT URL
    r = requests.post(f"{BASE_URL}/api/note/publish",
        headers={"Authorization": f"Bearer {token}"},
        json={"noteId": note_id, "fileName": file_name, "contentType": content_type},
        timeout=10)
    if r.status_code != 200:
        print(f"  发布笔记失败: {r.status_code} {r.text[:100]}")
        return False

    upload_url = r.json()["data"]["uploadUrl"]

    # 上传文件到 note-service 指定的 MinIO 路径
    with open(image_path, "rb") as f:
        r2 = requests.put(upload_url, data=f, headers={"Content-Type": content_type}, timeout=60)
    if not r2.ok:
        print(f"  上传封面图失败: {r2.status_code}")
        return False
    return True


# ─── 主流程 ─────────────────────────────────────────────

def main():
    print("=" * 60)
    print("  初始化测试数据")
    print("=" * 60)
    print()

    # 检查前置条件
    print("[1/4] 检查服务连通性 ...")
    try:
        r = requests.get(f"{BASE_URL}/api/auth/login", timeout=5)
    except requests.ConnectionError:
        print(f"  ERROR: 无法连接到 {BASE_URL}，请确认 gateway 已启动")
        sys.exit(1)
    print("  Gateway 连接 OK")

    if not os.path.isdir(ASSETS_DIR):
        print(f"  ERROR: 图片目录不存在: {ASSETS_DIR}")
        sys.exit(1)
    images = sorted([
        f for f in os.listdir(ASSETS_DIR)
        if os.path.splitext(f)[1].lower() in {".jpg", ".jpeg", ".png", ".webp"}
    ])
    print(f"  找到 {len(images)} 张测试图片")

    # ── 注册/登录所有用户 ──
    print()
    print("[2/4] 注册/登录用户 ...")
    user_tokens = {}
    for user in USERS:
        register_user(user)
        token = login(user)
        if token:
            user_tokens[user["username"]] = token
    print(f"  可用用户: {len(user_tokens)} 个")

    if not user_tokens:
        print("  ERROR: 没有用户成功登录")
        sys.exit(1)

    random.shuffle(images)

    # ── 生成笔记分配 ──
    print()
    print("[3/4] 生成笔记分配 ...")
    usernames = list(user_tokens.keys())

    # 确保每个用户至少分配到一篇笔记
    assignments = []
    for i in range(len(usernames)):
        assignments.append({"image": images[i], "username": usernames[i], "note": NOTES[i % len(NOTES)]})

    # 剩余图片随机分配
    for i in range(len(usernames), len(images)):
        img = images[i]
        note = NOTES[i % len(NOTES)]
        user = random.choice(usernames)
        assignments.append({"image": img, "username": user, "note": note})

    random.shuffle(assignments)

    # ── 执行发布 ──
    print()
    print(f"[4/4] 发布 {len(assignments)} 篇笔记 ...")
    print()

    success = 0
    for idx, a in enumerate(assignments):
        img = a["image"]
        username = a["username"]
        note = a["note"]
        token = user_tokens[username]

        file_name = os.path.basename(img)
        status = f"[{idx+1}/{len(assignments)}]"

        # 创建草稿
        note_id = create_draft(token, note["title"], note["content"])
        if not note_id:
            print(f"  {status} {username} 草稿失败 <- {file_name}")
            continue

        # 发布笔记 + 上传封面图（使用 note-service 返回的 presigned URL）
        ok = publish_and_upload(token, note_id, os.path.join(ASSETS_DIR, img))
        if ok:
            print(f"  {status} [OK] {username} 发布了「{note['title']}」({note['category']})")
            success += 1
        else:
            print(f"  {status} [FAIL] {username} 发布失败")

        # 控制节奏，避免打崩服务
        time.sleep(0.3)

    print()
    print("=" * 60)
    print(f"  完成！成功率: {success}/{len(assignments)}")
    print("=" * 60)
    print()
    print("  查看效果:")
    print(f"    Feed 页: {BASE_URL}/")
    print(f"    搜索页: {BASE_URL}/search?q=旅行")
    print()


if __name__ == "__main__":
    main()
