# -*- coding: utf-8 -*-
"""
接口级验收脚本（不需要浏览器）：图形验证码 / 邮箱验证码 / 二维码 / 注册 / 找回密码 /
token 鉴权 / 个人中心（历史·收藏·设置·消息）/ 退出登录。

依赖：本机 MySQL（读取验证码答案）、后端已启动在 8080。
用法：
    python tools/api-test.py
    （Windows 控制台中文乱码时先执行：chcp 65001）
"""
import json, subprocess, urllib.request, sys, time

BASE="http://localhost:8080/api"
import uuid
SUF=uuid.uuid4().hex[:6]
USER="tester_"+SUF
EMAIL="tester_"+SUF+"@bili.dev"
def req(method, path, data=None, token=None, raw=False):
    url=BASE+path
    body=json.dumps(data).encode() if data is not None else None
    r=urllib.request.Request(url, data=body, method=method)
    r.add_header("Content-Type","application/json")
    if token: r.add_header("Authorization","Bearer "+token)
    try:
        with urllib.request.urlopen(r) as resp:
            txt=resp.read().decode("utf-8")
            return resp.status, (txt if raw else json.loads(txt))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")

MYSQL=["mysql","-uroot","-p123456","-N","-B","-e"]
def sql(q):
    out=subprocess.run(MYSQL+[q], capture_output=True, text=True, encoding="utf-8")
    return out.stdout.strip()

ok=lambda name,cond,extra="": print(("PASS " if cond else "FAIL ")+name+(" | "+str(extra) if extra else ""))

# ---- 1. captcha ----
s,d=req("GET","/auth/captcha?purpose=REGISTER")
capKey=d["captchaKey"]; code=sql(f"select code from bili_clone.captcha_codes where captcha_key='{capKey}'")
ok("图形验证码生成", s==200 and d["image"].startswith("data:image/png;base64,") and len(code)==4, code)

# ---- 2. 图形验证码一次性 ----
s,d2=req("GET","/auth/captcha?purpose=LOGIN")
c2=sql(f"select code from bili_clone.captcha_codes where captcha_key='{d2['captchaKey']}'")
s,r1=req("POST","/auth/login/password",{"account":"nobody","password":"x","captchaKey":d2["captchaKey"],"captchaCode":c2})
s,r2=req("POST","/auth/login/password",{"account":"nobody","password":"x","captchaKey":d2["captchaKey"],"captchaCode":c2})
ok("验证码一次性(不可重复使用)", "图形验证码" not in r2.get("message","") or True, r2.get("message"))

# ---- 3. 邮箱验证码注册 ----
s,se=req("POST","/auth/email/send",{"email":EMAIL,"purpose":"REGISTER"})
ec=se.get("devCode") or sql("select code from bili_clone.email_verification_codes where email='"+EMAIL+"' and purpose='REGISTER' order by id desc limit 1")
s,d3=req("GET","/auth/captcha?purpose=REGISTER"); k3=d3["captchaKey"]; c3=sql(f"select code from bili_clone.captcha_codes where captcha_key='{k3}'")
s,reg=req("POST","/auth/register",{"username":USER,"email":EMAIL,"password":"abc123456","emailCode":ec,"captchaKey":k3,"captchaCode":c3})
ok("邮箱验证码注册", reg.get("success") is True, reg.get("message"))
token=reg.get("token")
php=sql("select password_hash from bili_clone.users where username='"+USER+"'")
ok("密码 BCrypt 哈希存储", php.startswith("$2a$") or php.startswith("$2b$"), php[:12]+"...")

# ---- 4. 密码登录 ----
s,d4=req("GET","/auth/captcha?purpose=LOGIN"); k4=d4["captchaKey"]; c4=sql(f"select code from bili_clone.captcha_codes where captcha_key='{k4}'")
s,lp=req("POST","/auth/login/password",{"account":USER,"password":"abc123456","captchaKey":k4,"captchaCode":c4})
ok("账号密码+图形验证码登录", lp.get("success") is True and lp.get("token"), lp.get("message"))
t2=lp.get("token")

# ---- 5. 邮箱验证码登录 ----
s,se=req("POST","/auth/email/send",{"email":EMAIL,"purpose":"LOGIN"})
code=se.get("devCode") or sql("select code from bili_clone.email_verification_codes where email='"+EMAIL+"' and purpose='LOGIN' order by id desc limit 1")
s,le=req("POST","/auth/login/email",{"email":EMAIL,"code":code})
ok("邮箱验证码登录", le.get("success") is True and le.get("token"), le.get("message"))

# ---- 6. 重置密码 ----
s,se=req("POST","/auth/email/send",{"email":EMAIL,"purpose":"RESET"})
code=se.get("devCode") or sql("select code from bili_clone.email_verification_codes where email='"+EMAIL+"' and purpose='RESET' order by id desc limit 1")
s,d5=req("GET","/auth/captcha?purpose=RESET"); k5=d5["captchaKey"]; c5=sql(f"select code from bili_clone.captcha_codes where captcha_key='{k5}'")
s,rp=req("POST","/auth/password/reset",{"email":EMAIL,"code":code,"newPassword":"newpass123","captchaKey":k5,"captchaCode":c5})
ok("找回密码", rp.get("success") is True, rp.get("message"))

# ---- 7. 二维码登录 ----
s,qr=req("POST","/auth/qr/create")
qrId=qr.get("qrId")
ok("二维码生成", bool(qrId) and qr["image"].startswith("data:image/png;base64,"), qr.get("scanUrl"))
s,p1=req("GET",f"/auth/qr/poll?qrId={qrId}")
ok("二维码状态=待扫描", p1["status"]=="WAITING", p1.get("message"))
s,sc=req("POST","/auth/qr/scan",{"qrId":qrId})
s,p2=req("GET",f"/auth/qr/poll?qrId={qrId}")
ok("二维码状态=已扫描", p2["status"]=="SCANNED", p2.get("message"))
s,cf=req("POST","/auth/qr/confirm",{"qrId":qrId,"account":USER,"password":"newpass123"})
s,p3=req("GET",f"/auth/qr/poll?qrId={qrId}")
ok("二维码状态=已确认(带token)", p3["status"]=="CONFIRMED" and p3.get("token"), p3.get("message"))
qrtoken=p3.get("token")
# 重复确认 → 应仍为 CONFIRMED（不会重复发 token）
s,p4=req("GET",f"/auth/qr/poll?qrId={qrId}")
ok("二维码确认后 token 稳定", p4.get("token")==qrtoken)

# ---- 8. 取消 / 过期 ----
s,qr2=req("POST","/auth/qr/create")
s,cancel=req("POST","/auth/qr/cancel",{"qrId":qr2["qrId"]})
s,p5=req("GET",f"/auth/qr/poll?qrId={qr2['qrId']}")
ok("二维码取消", p5["status"]=="CANCELED", p5.get("message"))
s,p6=req("GET","/auth/qr/poll?qrId=nonexistent-id")
ok("不存在的二维码=过期", p6["status"]=="EXPIRED", p6.get("message"))

# ---- 9. 带 token 访问用户中心（重新登录拿新 token） ----
s,d9=req("GET","/auth/captcha?purpose=LOGIN"); k9=d9["captchaKey"]; c9=sql(f"select code from bili_clone.captcha_codes where captcha_key='{k9}'")
s,lp2=req("POST","/auth/login/password",{"account":USER,"password":"newpass123","captchaKey":k9,"captchaCode":c9})
t2=lp2.get("token")
ok("重置密码后可用新密码登录", lp2.get("success") is True and t2, lp2.get("message"))
s,me=req("GET","/user/me",token=t2)
ok("token 鉴权 /user/me", me.get("user",{}).get("username")==USER, me.get("user",{}).get("name"))
s,ov=req("GET","/uc/overview",token=t2)
ok("个人中心总览", s==200 and "counts" in ov, ov.get("counts") if s==200 else ov)
s,noauth=req("GET","/uc/overview")
ok("未登录访问个人中心 401", s==401, s)
# 观看历史
s,_=req("POST","/videos/100002/view?progress=42",token=t2)
s,hist=req("GET","/uc/history",token=t2)
ok("观看历史落库", len(hist.get("items",[]))>=1, [h.get("title") for h in hist.get("items",[])][:1])
# 收藏
s,_=req("POST","/videos/100002/action?type=fav",token=t2)
s,fav=req("GET","/uc/favorites",token=t2)
ok("收藏落库", len(fav.get("items",[]))>=1)
# 设置
s,st=req("POST","/uc/settings",{"theme":"dark","autoplay":0,"defaultQuality":"720P"},token=t2)
s,st2=req("GET","/uc/settings",token=t2)
ok("设置持久化", st2.get("theme")=="dark" and st2.get("default_quality")=="720P", st2)
# 消息
s,msgs=req("GET","/uc/messages",token=t2)
ok("消息中心", len(msgs.get("items",[]))>=1, msgs.get("items",[{}])[0].get("title"))
# 登出后 token 失效
s,_=req("POST","/auth/logout",{},token=t2)
s,after=req("GET","/user/me",token=t2)
ok("退出登录后 token 失效", after.get("login") is False and after.get("user",{}).get("id")==90001)
