cmake_minimum_required(VERSION 3.10.2)
project("apriltag_android")

add_library(apriltag SHARED
        apriltag.c
        apriltag_pose.c
        apriltag_pywrap.c
        apriltag_quad_thresh.c
        tag16h5.c
        tag36h10.c
        tag25h9.c
        tag36h11.c
        tagCircle21h7.c
        tagCircle49h12.c
        tagCustom48h12.c
        tagStandard41h12.c
        tagStandard52h13.c
)

find_library(log-lib log)

target_link_libraries(apriltag ${log-lib})
