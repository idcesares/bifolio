# Images next to the document

The pictures below live in the `img` folder beside this file. They load only after the reader grants that folder (UI-14).

![A wide gradient](img/wide.png)

![A tall gradient](./img/tall.png "A tall picture")

![A file name with a space](img/with%20space.png)

![A photo stored sideways, EXIF orientation 6](img/rotated-exif.jpg)

![A plain JPEG](img/plain.jpg)

![A file that does not exist](img/missing.png)

These references must never load, so only their alt text shows.

![Climbs above the granted folder](../outside.png)

![Absolute path](/etc/passwd)

![Remote picture](https://example.com/picture.png)

![Local file URL](file:///sdcard/picture.png)

A picture ![inside](img/wide.png) a sentence stays part of the paragraph.
