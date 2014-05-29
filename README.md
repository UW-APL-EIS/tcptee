tcptee
======

Sit inline between a TCP client and server, printing out all data flow to stdout as it goes by. Think Unix 'tee'.  Example

Firefox -> google.com:80 -> Google

becomes

Firefox -> localhost:8080 -> TCPTee 8080 google.com 80 -> Google


