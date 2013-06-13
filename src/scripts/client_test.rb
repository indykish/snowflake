#!/usr/bin/ruby 
require 'rubygems'
$:.push('./gen-rb')
require 'thrift'
require 'snowflake'

if ARGV.length < 3
  puts "client_test.rb <count> <servers> <agent>"
  exit
end
count   = ARGV.shift.to_i
servers = ARGV.shift
agent   = ARGV.shift

host, port = servers.split(/,/).first.split(/:/)
p host
p port

socket = Thrift::Socket.new(host, port.to_i, timeout=nil)
socket.open

connection = Thrift::FramedTransport.new(socket)

protocol=Thrift::BinaryProtocol.new(connection)

client = Snowflake::Client.new(protocol)

worker_id = client.get_id(agent)

count.times do |i|
  
   #  puts [client.get_id(agent).to_s, agent, worker_id.to_s].join(' ')

     puts [agent, worker_id.to_s].join('')

end
