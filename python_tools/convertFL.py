import json
import os
pointList = []
data = json.load(open('python_tools/in.json'))
for item in range(1, len(data['points']) - 1):
  pointList.append({
    "longitude": data['points'][item]['longitude'],
    "latitude": data['points'][item]['latitude'],
  })
json.dump({'nodes': pointList, 'name': 'CDU'}, open('python_tools/out.json', 'w'), indent=2)
