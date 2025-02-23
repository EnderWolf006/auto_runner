import 'dart:convert';
import 'dart:io';

import 'package:audioplayers/audioplayers.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:localstorage/localstorage.dart';
import 'package:prompt_dialog/prompt_dialog.dart';

const double defaultSpeed = 2.5;
const double defaultRandomOffset = 0.5;
const double defaultUpdateFrequency = 0.5;
const double defaultCandence = 3.0;

//flutter build apk --release

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initLocalStorage();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'AutoRunner',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'Auto Runner'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});
  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  String? routeJson;
  double? speed; // m/s
  double? randomOffset; // m
  double? updateFrequency; // s
  double? cadence; // steps/s

  final ScrollController _scrollController = ScrollController();

  List<Map<String, dynamic>> logs = [
    {
      "message": "Welcome to AutoRunner.\nPlease set \"Mock location app\" to AutoRunner first if you haven't done it yet.",
      "color": Colors.blue,
      "time": DateTime.now()
    }
  ];

  List<Map<String, String>> dashboards = [
    {"title": "Pace(min/km)", "value": "??"},
    {"title": "Speed(m/s)", "value": "??"},
    {"title": "Cadence(steps/s)", "value": "??"},
    {"title": "Countdown(m:s)", "value": "??"},
  ];

  double totalDistance = 0;
  double nowDistance = 0;

  bool running = false;
  AudioPlayer player = AudioPlayer();

  static const channel = MethodChannel('Flutter.MethodChannel');

  @override
  void initState() {
    super.initState();
    routeJson = localStorage.getItem('routeJson');
    String? strSpeed = localStorage.getItem('speed');
    if (strSpeed == null) {
      speed = defaultSpeed;
      localStorage.setItem('speed', speed.toString());
    } else {
      speed = double.tryParse(strSpeed);
    }
    String? strRandomOffset = localStorage.getItem('randomOffset');
    if (strRandomOffset == null) {
      randomOffset = defaultRandomOffset;
      localStorage.setItem('randomOffset', randomOffset.toString());
    } else {
      randomOffset = double.tryParse(strRandomOffset);
    }
    String? strUpdateFrequency = localStorage.getItem('updateFrequency');
    if (strUpdateFrequency == null) {
      updateFrequency = defaultUpdateFrequency;
      localStorage.setItem('updateFrequency', updateFrequency.toString());
    } else {
      updateFrequency = double.tryParse(strUpdateFrequency);
    }
    String? strCadence = localStorage.getItem('cadence');
    if (strCadence == null) {
      cadence = defaultCandence;
      localStorage.setItem('cadence', cadence.toString());
    } else {
      cadence = double.tryParse(strCadence);
    }
  }

  void log(String message, {Color color = Colors.black}) {
    setState(() {
      logs.add({
        "message": message,
        "color": color,
        "time": DateTime.now(),
      });
      moveLog();
    });
  }

  void moveLog() {
    Future.delayed(Duration(milliseconds: 200), () {
      _scrollController.position.moveTo(
        _scrollController.position.maxScrollExtent,
        duration: Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    });
  }

  void toggle() async {
    if (routeJson == null) {
      log("Please select a route file first", color: Colors.red);
      return;
    }
    running = !running;
    setState(() {
      running = running;
      moveLog();
    });
    await channel.invokeMethod('toggleChanged', {
      "state": running,
      "routeJson": routeJson,
      "speed": speed,
      "randomOffset": randomOffset,
      "updateFrequency": updateFrequency,
      "cadence": cadence,
    }).onError((error, stackTrace) {
      log("Error: $error", color: Colors.red);
    }).then((value) {
      log("Toggled to ${running ? 'running' : 'stop'} successful", color: Colors.green);
    });
    if (running) {
      Future.doWhile(() async {
        tick();
        await Future.delayed(Duration(seconds: 1));
        return running;
      });
      try {
        await player.stop();
      } catch (e) {
        log("Error: $e", color: Colors.red);
      }
    }
  }

  void tick() {
    channel.invokeMethod('getInfo', {}).onError((error, stackTrace) {
      log("Error: $error", color: Colors.red);
    }).then((value) async {
      if (value['isMoving'] == false) {
        log("Route finished", color: Colors.orange);
        running = false;
        await player.setSource(AssetSource('finish.mp3'));
        await player.resume();
      }
      setState(() {
        dashboards[0]['value'] = ((1000.0 / (value['speed']) / 6).round() / 10.0).toString();
        dashboards[1]['value'] = value['speed'].toString();
        dashboards[2]['value'] = value['cadence'].toString();
        var sec = ((value['startTime'] / 1000 + value['totalDistance'] / value['speed'] - DateTime.now().millisecondsSinceEpoch / 1000) as double).round();
        dashboards[3]['value'] = '${(sec ~/ 60).toString().padLeft(2, '0')}:${(sec % 60).toString().padLeft(2, '0')}';
        totalDistance = value['totalDistance'];
        nowDistance = value['distanceTravelled'];
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      resizeToAvoidBottomInset: false,
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
        elevation: 4,
        shadowColor: Theme.of(context).colorScheme.primary.withAlpha(128),
      ),
      body: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          if (running)
            Container(
              child: Column(
                children: [
                  Container(
                    padding: EdgeInsets.all(12),
                    child: Column(
                      children: [
                        Text(
                          "Dashboard",
                          style: TextStyle(fontSize: 28),
                        ),
                        Container(
                          margin: EdgeInsets.only(top: 12),
                          padding: EdgeInsets.all(14),
                          decoration: BoxDecoration(color: Theme.of(context).colorScheme.primary.withAlpha(24), borderRadius: BorderRadius.circular(6)),
                          child: Column(
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                crossAxisAlignment: CrossAxisAlignment.end,
                                children: [
                                  Text(
                                    (nowDistance / 1000).toStringAsFixed(2),
                                    style: TextStyle(fontSize: 48, fontWeight: FontWeight.bold, height: 1),
                                  ),
                                  Text("/${(totalDistance / 1000).toStringAsFixed(2)}KM",
                                      style: TextStyle(fontSize: 22, height: 1.4, fontWeight: FontWeight.bold)),
                                ],
                              ),
                              Padding(
                                padding: const EdgeInsets.only(top: 8, bottom: 8),
                                child: Text("${(nowDistance / totalDistance * 100).toStringAsFixed(2)}%",
                                    style: TextStyle(fontSize: 18, color: Theme.of(context).colorScheme.primary, fontWeight: FontWeight.bold, height: 1)),
                              ),
                              LinearProgressIndicator(
                                value: nowDistance / totalDistance,
                              ),
                            ],
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.only(top: 12),
                          child: GridView.builder(
                            itemBuilder: (context, index) {
                              return Container(
                                padding: EdgeInsets.all(6),
                                height: 1,
                                width: 10,
                                decoration: BoxDecoration(color: Theme.of(context).colorScheme.primary.withAlpha(24), borderRadius: BorderRadius.circular(6)),
                                child: Center(
                                  child: Column(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    crossAxisAlignment: CrossAxisAlignment.center,
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Text(dashboards[index]['value']!, style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
                                      Text(dashboards[index]['title']!, style: TextStyle(fontSize: 16)),
                                    ],
                                  ),
                                ),
                              );
                            },
                            physics: NeverScrollableScrollPhysics(),
                            shrinkWrap: true,
                            itemCount: dashboards.length,
                            gridDelegate:
                                SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 2, mainAxisSpacing: 10, crossAxisSpacing: 10, mainAxisExtent: 80),
                          ),
                        )
                      ],
                    ),
                  ),
                ],
              ),
            ),
          if (!running)
            Container(
              padding: EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Text(
                    "Settings",
                    style: TextStyle(fontSize: 28),
                  ),
                  Container(
                      margin: EdgeInsets.only(top: 12),
                      padding: EdgeInsets.all(6),
                      decoration: BoxDecoration(color: Theme.of(context).colorScheme.primary.withAlpha(24), borderRadius: BorderRadius.circular(6)),
                      child: Column(
                        children: [
                          ListTile(
                            title: Text("Route Json File"),
                            subtitle: Text(
                              routeJson == null
                                  ? "No file selected"
                                  : "Selected: ${jsonDecode(routeJson!)['name']} (${jsonDecode(routeJson!)['nodes'].length} nodes)",
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            trailing: Icon(Icons.file_upload_rounded),
                            onTap: () async {
                              FilePickerResult? result = await FilePicker.platform.pickFiles();
                              if (result == null) return;
                              File file = File(result.files.single.path!);
                              setState(() {
                                routeJson = file.readAsStringSync();
                                localStorage.setItem('routeJson', routeJson!);
                                log("Route file selected");
                              });
                            },
                          ),
                          ListTile(
                            title: Text("Speed"),
                            subtitle: Text('${speed}m/s (${(1000.0 / (speed ?? defaultSpeed) / 6).round() / 10.0}min/km)'),
                            trailing: Icon(Icons.chevron_right_rounded),
                            onTap: () {
                              prompt(context, title: Text('Speed Setting'), initialValue: (speed ?? defaultSpeed).toString(), validator: (p0) {
                                if (p0 == null) {
                                  return 'Please enter a number';
                                }
                                if (double.tryParse(p0) == null) {
                                  return 'Please enter a valid number';
                                }
                                return null;
                              }, hintText: 'm/s')
                                  .then((value) {
                                if (value == null) {
                                  return;
                                }
                                setState(() {
                                  speed = double.tryParse(value);
                                  localStorage.setItem('speed', value);
                                  log("Speed set to $value m/s");
                                });
                              });
                            },
                          ),
                          ListTile(
                            title: Text("Cadence"),
                            subtitle: Text('${cadence}steps/s'),
                            trailing: Icon(Icons.chevron_right_rounded),
                            onTap: () {
                              prompt(context, title: Text('Cadence Setting'), initialValue: (cadence ?? defaultCandence).toString(), validator: (p0) {
                                if (p0 == null) {
                                  return 'Please enter a number';
                                }
                                if (double.tryParse(p0) == null) {
                                  return 'Please enter a valid number';
                                }
                                return null;
                              }, hintText: 'steps/s')
                                  .then((value) {
                                if (value == null) {
                                  return;
                                }
                                setState(() {
                                  cadence = double.tryParse(value);
                                  localStorage.setItem('cadence', value);
                                  log("Cadence set to $value steps/s");
                                });
                              });
                            },
                          ),
                          ListTile(
                            title: Text("Random Offset"),
                            subtitle: Text('Less than ${randomOffset ?? '?'}m'),
                            trailing: Icon(Icons.chevron_right_rounded),
                            onTap: () {
                              prompt(context, title: Text('Offset Setting'), initialValue: (randomOffset ?? defaultRandomOffset).toString(), validator: (p0) {
                                if (p0 == null) {
                                  return 'Please enter a number';
                                }
                                if (double.tryParse(p0) == null) {
                                  return 'Please enter a valid number';
                                }
                                return null;
                              }, hintText: 'm')
                                  .then((value) {
                                if (value == null) {
                                  return;
                                }
                                setState(() {
                                  randomOffset = double.tryParse(value);
                                  localStorage.setItem('randomOffset', value);
                                  log("Offset set to ${value}m/s");
                                });
                              });
                            },
                          ),
                          ListTile(
                            title: Text("Update Frequency"),
                            subtitle: Text('${updateFrequency}s'),
                            trailing: Icon(Icons.chevron_right_rounded),
                            onTap: () {
                              prompt(context, title: Text('Frequency Setting'), initialValue: (updateFrequency ?? defaultUpdateFrequency).toString(),
                                      validator: (p0) {
                                if (p0 == null) {
                                  return 'Please enter a number';
                                }
                                if (double.tryParse(p0) == null) {
                                  return 'Please enter a valid number';
                                }
                                return null;
                              }, hintText: 's')
                                  .then((value) {
                                if (value == null) {
                                  return;
                                }
                                setState(() {
                                  updateFrequency = double.tryParse(value);
                                  localStorage.setItem('updateFrequency', value);
                                  log("Update frequency set to ${value}s");
                                });
                              });
                            },
                          ),
                        ],
                      )),
                ],
              ),
            ),
          Expanded(
              flex: 1,
              child: Container(
                padding: EdgeInsets.all(12),
                decoration: BoxDecoration(color: Color.fromARGB(255, 214, 226, 251)),
                child: Container(
                  padding: EdgeInsets.all(6),
                  decoration: BoxDecoration(color: Color.fromARGB(88, 255, 255, 255), borderRadius: BorderRadius.circular(6)),
                  child: ListView.builder(
                    controller: _scrollController,
                    itemCount: logs.length,
                    itemBuilder: (context, index) {
                      return Text(
                        '[${logs[index]['time'].toLocal().toString().substring(11, 19)}] ${logs[index]["message"]}',
                        style: TextStyle(color: logs[index]["color"]),
                      );
                    },
                  ),
                ),
              )),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: toggle,
        tooltip: 'Toggle',
        child: running ? const Icon(Icons.pause) : const Icon(Icons.play_arrow),
      ),
    );
  }
}
