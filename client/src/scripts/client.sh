
CLIENT_NAME=$1


echo Creating client $CLIENT_NAME

curl -s -X POST http://127.0.0.1:12552/create --form "clientName=$CLIENT_NAME"


CLIENT_ID=$(curl -s -X POST http://127.0.0.1:12552/create --form "clientName=$CLIENT_NAME" | jq -r '.client.id.value')


echo Created client $CLIENT_NAME with id $CLIENT_ID

curl -s -X GET  http://127.0.0.1:12551/find --form "clientId=$CLIENT_ID"
