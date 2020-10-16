
curl -s  http://127.0.0.1:12552/create/Julius

sleep 10

curl -s http://127.0.0.1:12551/find/Julius

curl -s -X POST --form 'from=Oliver' http://127.0.0.1:12552/hello/Julius

curl -s -X POST --form 'food=apple' -k http://127.0.0.1:12551/feed/Julius

curl -s -X POST --form 'lesson=bing' -k http://127.0.0.1:12552/teach/Julius

curl -s http://127.0.0.1:12551/find/Julius

curl -s http://127.0.0.1:12552/status/Julius