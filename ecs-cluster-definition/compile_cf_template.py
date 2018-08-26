import os
import sys
import yaml
from jinja2 import Environment, FileSystemLoader, StrictUndefined

# Capture our current directory
THIS_DIR = os.path.dirname(os.path.abspath(__file__))
TEMPLATE_DIR = os.path.join(THIS_DIR, 'templates')


def compile_and_save_to_file(_stage, _params_file, _destination_file):
    j2_env = Environment(loader=FileSystemLoader(TEMPLATE_DIR),
                         undefined=StrictUndefined,
                         trim_blocks=True)
    print "Load parameters from: " + _params_file
    with open(_params_file, 'r') as stream:
        params = yaml.load(stream)

    # Add computed params
    params["STAGE"] = _stage
    params["CLUSTER_NAME"] = "dpnt-video-"+_stage

    print "Compiling cloudformation template"
    output = j2_env.get_template('cf-master.jinja2.json').render(**params)
    print "Saving output to file: " + _destination_file
    with open(_destination_file, 'w') as f:
        f.write(output)


if __name__ == '__main__':

    if len(sys.argv) < 4:
        raise Exception("No destination provided. Usage: " + sys.argv[0] +
                        " <stage>"
                        " <input_parameters_file>" +
                        " <template_destination>")

    stage = sys.argv[1]
    input_parameters_file = sys.argv[2]
    destination_file = sys.argv[3]
    compile_and_save_to_file(stage, input_parameters_file, destination_file)
