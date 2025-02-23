# Auto Runner
An Android(Flutter) application that can mock GPS location along a set route.
> Warning: This project is used for test GPS function on your **own** application, PLEASE DO NOT USE IT FOR ILLEGAL PURPOSE

## Requirement
- Android device(At least Android 11)
- Xposed(Root)
- Settings `Dev options` -> `Location` -> `Select mock location app` -> `auto_runner`

## Route Json File
Before starting, you have to make or select a `Route Json File`. Its format is as follows.
```json
{
  "nodes": [
    {
      "longitude": 104.19430631773561,
      "latitude": 30.657094881112435
    },
    {
      "longitude": 104.19430631773561,
      "latitude": 30.657094881112435
    },
    //...
    {
      "longitude": 104.1978800954528,
      "latitude": 30.6564003955068
    }
  ],
  "name": "CDU"
}
```
And there is a tools in `python_tools/convertFL.py`. It can convert `Fake Location`'s route format to `Auto Runner`'s format.
