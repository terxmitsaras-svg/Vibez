cmake_minimum_required(VERSION 3.22.1)
project(terminalpty C)

add_library(terminalpty SHARED pty.c)
target_compile_options(terminalpty PRIVATE -std=gnu11 -Wall -Wextra -Werror -fvisibility=hidden)
# Required for devices using 16 KB memory pages.
target_link_options(terminalpty PRIVATE -Wl,-z,max-page-size=16384)
