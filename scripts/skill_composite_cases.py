"""Six explicit local resource pairs, including healthy and degraded directory observations."""

def resource_pair(number):
    if not 1 <= number <= 6:
        raise ValueError('Choose an explicit pair 1..6')
    # Pair 3 exercises the existing DEGRADED resource, without modifying its state.
    directory = 7 if number == 3 else number
    return (f'discovery-service-{directory:02d}',
            f'ops-acc-a-service-{(number - 1) % 3 + 1}', f'ops-acc-a-order-{number}')
