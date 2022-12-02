# This script helps add corresponding fx graph operations before each kernel in cpp output code.

# input files
runnable_graph_file = "fx_graph_runnable.py"
output_code_file = "output_code.py"
# output file
hint_file = "graph_matching_output_code.py"

# For operations which are excluded from kernels, map their names from cpp to fx graph. Feel free to add more if needed.
cpp_2_graph_operation_dict = {
    "aten.convolution": "torch.ops.aten.convolution",
    "torch.ops.mkldnn._convolution_pointwise.binary": "torch.ops.mkldnn._convolution_pointwise.binary",
    "aten.bmm": "aten.bmm",
    "torch.ops.mkl._mkl_linear": "torch.ops.mkl._mkl_linear.default"
}

kernel_2_python_dict = {}

with open(runnable_graph_file, 'r') as graph_f, \
        open(output_code_file, 'r') as cpp_f:
    graph_lines = graph_f.readlines()
    cpp_lines = cpp_f.readlines()

graph_line_id = 0
while "forward" not in graph_lines[graph_line_id]:
    graph_line_id += 1
graph_line_id += 1

cpp_line_id = 0
while "call" not in cpp_lines[cpp_line_id]:
    cpp_line_id += 1
cpp_line_id += 1

graph_line_container = []
kernel_name = ""

while cpp_line_id < len(cpp_lines) and graph_line_id < len(graph_lines):
    match_substr = ""
    while cpp_line_id < len(cpp_lines) and match_substr == "":
        if "kernel_cpp" in cpp_lines[cpp_line_id]:
            kernel_name = cpp_lines[cpp_line_id][0:cpp_lines[cpp_line_id].find('(')].strip()
        for substr in cpp_2_graph_operation_dict:
            if substr in cpp_lines[cpp_line_id]:
                match_substr = substr
                break
        cpp_line_id += 1
    if cpp_line_id >= len(cpp_lines):
        break
    while graph_line_id < len(graph_lines) and cpp_2_graph_operation_dict[match_substr] not in graph_lines[graph_line_id]:
        graph_line_container.append('#line ' + str(graph_line_id+1) + ': ' + graph_lines[graph_line_id].strip() + '\n')
        graph_line_id += 1
    if kernel_name != "":
        kernel_2_python_dict[kernel_name] = graph_line_container
    
    graph_line_container = []
    kernel_name = ""
    cpp_line_id += 1
    graph_line_id += 1
while graph_line_id < len(graph_lines) and "return" not in graph_lines[graph_line_id]:
    graph_line_container.append('#line ' + str(graph_line_id+1) + ': ' + graph_lines[graph_line_id].strip() + '\n')
    graph_line_id += 1

if kernel_name != "" and len(graph_line_container) > 0:
    kernel_2_python_dict[kernel_name] = graph_line_container

with open(hint_file, 'w') as hint_f:
    for cpp_line_id in range(len(cpp_lines)):
        if "async_compile.cpp" in cpp_lines[cpp_line_id]:
            kernel_name = cpp_lines[cpp_line_id].split(" =", 1)[0]
            for v in kernel_2_python_dict[kernel_name]:
                hint_f.write(v)
        hint_f.write(cpp_lines[cpp_line_id])
